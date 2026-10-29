package com.company.crm.ai.service;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.dto.response.CustomerResDto;
import com.company.crm.customer.service.CustomerService;
import com.company.crm.notification.service.StaffNotifier;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.support.event.TicketFeedbackReceivedEvent;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FR-AI-6.2: which customers are at risk of leaving, and why. A score from 0 to 100, worked out
 * inside the CRM (nothing goes to an AI provider) from: messages the AI read as unhappy (FR-AI-6.1),
 * poor reviews of service, open and overdue service tickets, buying less often than usual, going
 * quiet, and lost deals or declined quotations. 50+ is high risk, 25+ medium. Stored on the
 * customer; when it turns high the owner and their team's manager are told, once per rise
 * (the tenant's admins when the customer has no owner). Refreshed as new sentiment or feedback
 * arrives and by {@link com.company.crm.ai.scheduler.ChurnRiskRefreshJob}.
 */
@Service
@RequiredArgsConstructor
public class ChurnRiskService {

    static final int HIGH = 50;
    static final int MEDIUM = 25;
    private static final String OPEN_TICKET = "status IN ('open', 'assigned', 'in_progress')";

    public record ChurnFactor(String name, int points, String detail) {}

    public record ChurnRisk(Long customerId, String customerName, Long ownerId, String ownerName, String level, int score,
                            List<ChurnFactor> factors, LocalDateTime assessedAt) {}

    private record Base(Long id, String name, Long ownerId, String ownerName, String storedLevel) {}

    private final JdbcTemplate jdbc;
    private final CustomerService customerService;
    private final UserRepository userRepository;
    private final StaffNotifier staffNotifier;

    // ==================== for users ====================

    /** One customer the caller can see, assessed now (and stored). */
    @Transactional
    public ChurnRisk assess(User user, Long customerId) {
        CustomerResDto customer = customerService.getCustomer(user, customerId);
        List<ChurnRisk> risks = refresh(user.getTenant().getId(), customer.getId());
        if (risks.isEmpty()) {
            throw ApiException.badRequest("Churn risk is assessed for active customers only");
        }
        return risks.get(0);
    }

    /** The customers the caller can see at {@code minLevel} (medium by default) or above, riskiest first. */
    @Transactional(readOnly = true)
    public List<ChurnRisk> atRisk(User user, String minLevel) {
        List<String> levels = "high".equals(minLevel) ? List.of("high") : "low".equals(minLevel) ? List.of("low", "medium", "high") : List.of("medium", "high");
        Set<Long> visible = customerService.listCustomers(user, null, "active", null).stream().map(CustomerResDto::getId).collect(Collectors.toSet());
        return jdbc.query("SELECT c.id, c.company_name, c.owner_id, u.full_name, c.churn_risk, c.churn_score, c.churn_reasons, c.churn_assessed_at "
                        + "FROM customers c LEFT JOIN users u ON u.id = c.owner_id WHERE c.tenant_id = ? AND c.churn_risk = ANY (?)",
                (rs, i) -> new ChurnRisk(rs.getLong("id"), rs.getString("company_name"), (Long) rs.getObject("owner_id"), rs.getString("full_name"),
                        rs.getString("churn_risk"), rs.getInt("churn_score"), parse(rs.getString("churn_reasons")),
                        rs.getTimestamp("churn_assessed_at").toLocalDateTime()),
                user.getTenant().getId(), levels.toArray(String[]::new)).stream()
                .filter(r -> visible.contains(r.customerId()))
                .sorted(Comparator.comparingInt(ChurnRisk::score).reversed())
                .toList();
    }

    // ==================== keeping it current ====================

    /** After a message's sentiment was stored (FR-AI-6.1). */
    @Transactional
    public void refreshForMessage(Long communicationId) {
        jdbc.query("SELECT tenant_id, customer_id FROM communication_logs WHERE id = ? AND customer_id IS NOT NULL",
                rs -> { refresh(rs.getLong(1), rs.getLong(2)); }, communicationId);
    }

    /** After a ticket's feedback (or its sentiment) was recorded. */
    @Transactional
    public void refreshForTicket(Long ticketId) {
        jdbc.query("SELECT tenant_id, customer_id FROM service_tickets WHERE id = ?", rs -> { refresh(rs.getLong(1), rs.getLong(2)); }, ticketId);
    }

    /** A poor rating counts even before (or without) its sentiment being read. */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFeedback(TicketFeedbackReceivedEvent event) {
        refreshForTicket(event.ticketId());
    }

    /** Every active customer of the tenant (the refresh job); returns how many were assessed. */
    @Transactional
    public int refreshTenant(Long tenantId) {
        return refresh(tenantId, null).size();
    }

    /** Assesses the tenant's active customers (or one), stores the result and flags those that just turned high. */
    List<ChurnRisk> refresh(Long tenantId, Long customerId) {
        List<ChurnRisk> risks = assessAll(tenantId, customerId);
        Map<Long, String> before = new HashMap<>();
        bases(tenantId, customerId).forEach(b -> before.put(b.id(), b.storedLevel()));
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<ChurnRisk> flagged = risks.stream().filter(r -> "high".equals(r.level()) && !"high".equals(before.get(r.customerId()))).toList();
        jdbc.batchUpdate("UPDATE customers SET churn_risk = ?, churn_score = ?, churn_reasons = ?, churn_assessed_at = ?, "
                + "churn_flagged_at = CASE WHEN ? THEN ? ELSE churn_flagged_at END WHERE id = ?", risks.stream()
                .map(r -> new Object[]{r.level(), r.score(), format(r.factors()), now, flagged.contains(r), now, r.customerId()}).toList());
        flagged.forEach(r -> flag(tenantId, r));
        return risks;
    }

    // ==================== the score ====================

    private List<ChurnRisk> assessAll(Long tenantId, Long customerId) {
        List<Base> customers = bases(tenantId, customerId);
        if (customers.isEmpty()) {
            return List.of();
        }
        String one = customerId != null ? " AND customer_id = ?" : "";
        Object[] args = customerId != null ? new Object[]{tenantId, customerId} : new Object[]{tenantId};
        Map<Long, long[]> messages = longs("SELECT customer_id, count(*) FILTER (WHERE sentiment = 'negative'), count(*) FILTER (WHERE churn_risk = 'high') "
                + "FROM communication_logs WHERE tenant_id = ? AND customer_id IS NOT NULL AND direction = 'inbound' AND sentiment IS NOT NULL "
                + "AND occurred_at >= now() - interval '90 days'" + one + " GROUP BY customer_id", 2, args);
        Map<Long, long[]> reviews = longs("SELECT customer_id, coalesce(min(feedback_score), 5), count(*) FILTER (WHERE feedback_sentiment = 'negative'), "
                + "count(*) FILTER (WHERE feedback_churn_risk = 'high') FROM service_tickets WHERE tenant_id = ? AND feedback_at >= now() - interval '180 days'"
                + one + " GROUP BY customer_id", 3, args);
        Map<Long, long[]> tickets = longs("SELECT customer_id, count(*), count(*) FILTER (WHERE sla_due_at < now()) FROM service_tickets "
                + "WHERE tenant_id = ? AND " + OPEN_TICKET + one + " GROUP BY customer_id", 2, args);
        // purchases: how many, days since the last, days from the first to the last
        Map<Long, long[]> purchases = longs("SELECT customer_id, count(*), extract(day FROM now() - max(customer_approved_at))::bigint, "
                + "extract(day FROM max(customer_approved_at) - min(customer_approved_at))::bigint FROM quotations "
                + "WHERE tenant_id = ? AND status = 'approved' AND customer_approved_at IS NOT NULL" + one + " GROUP BY customer_id", 3, args);
        Map<Long, long[]> heardFrom = longs("SELECT customer_id, extract(day FROM now() - max(occurred_at))::bigint FROM communication_logs "
                + "WHERE tenant_id = ? AND customer_id IS NOT NULL AND direction = 'inbound'" + one + " GROUP BY customer_id", 1, args);
        Map<Long, long[]> lostDeals = longs("SELECT customer_id, count(*) FROM opportunities WHERE tenant_id = ? AND stage = 'lost' "
                + "AND stage_changed_at >= now() - interval '90 days'" + one + " GROUP BY customer_id", 1, args);
        Map<Long, long[]> declined = longs("SELECT customer_id, count(*) FROM quotations WHERE tenant_id = ? AND status = 'rejected' "
                + "AND coalesce(customer_responded_at, updated_at) >= now() - interval '90 days'" + one + " GROUP BY customer_id", 1, args);

        LocalDateTime now = LocalDateTime.now();
        List<ChurnRisk> out = new ArrayList<>();
        for (Base c : customers) {
            List<ChurnFactor> factors = new ArrayList<>();
            long[] m = messages.get(c.id());
            if (m != null && m[1] > 0) {
                factors.add(new ChurnFactor("Unhappy messages", 35, m[1] + " message(s) in the last 90 days read as likely to leave"));
            } else if (m != null && m[0] > 0) {
                factors.add(new ChurnFactor("Unhappy messages", (int) Math.min(2, m[0]) * 12, m[0] + " negative message(s) in the last 90 days"));
            }
            long[] r = reviews.get(c.id());
            if (r != null && r[2] > 0) {
                factors.add(new ChurnFactor("Poor review", 25, "A service review read as likely to leave (lowest rating " + r[0] + "/5)"));
            } else if (r != null && (r[0] <= 2 || r[1] > 0)) {
                factors.add(new ChurnFactor("Poor review", 20, "Rated a recent service " + r[0] + "/5" + (r[1] > 0 ? ", with a negative comment" : "")));
            }
            long[] t = tickets.get(c.id());
            if (t != null && t[0] > 0) {
                int points = (int) Math.min(20, Math.min(15, 5 * t[0]) + (t[1] > 0 ? 10 : 0));
                factors.add(new ChurnFactor("Open problems", points, t[0] + " open service ticket(s)" + (t[1] > 0 ? ", " + t[1] + " past the SLA" : "")));
            }
            long[] p = purchases.get(c.id());
            if (p != null && p[0] > 0) {
                long usual = p[0] >= 2 ? Math.max(1, p[2] / (p[0] - 1)) : 180;
                if (p[1] > Math.max(60, usual * 3 / 2)) {
                    factors.add(new ChurnFactor("Buying less", 20, "No purchase for " + p[1] + " days"
                            + (p[0] >= 2 ? " (usually every " + usual + ")" : "")));
                }
            }
            long[] h = heardFrom.get(c.id());
            if (h != null && h[0] > 90) {
                factors.add(new ChurnFactor("Gone quiet", 10, "No message from them in " + h[0] + " days"));
            }
            long lost = lostDeals.getOrDefault(c.id(), new long[]{0})[0];
            long no = declined.getOrDefault(c.id(), new long[]{0})[0];
            if (lost + no > 0) {
                int points = (int) Math.min(20, Math.min(15, 10 * lost) + Math.min(10, 5 * no));
                factors.add(new ChurnFactor("Lost business", points, (lost > 0 ? lost + " deal(s) lost" : "")
                        + (lost > 0 && no > 0 ? " and " : "") + (no > 0 ? no + " quotation(s) declined" : "") + " in the last 90 days"));
            }
            factors.sort(Comparator.comparingInt(ChurnFactor::points).reversed());
            int score = Math.min(100, factors.stream().mapToInt(ChurnFactor::points).sum());
            out.add(new ChurnRisk(c.id(), c.name(), c.ownerId(), c.ownerName(), level(score), score, factors, now));
        }
        return out;
    }

    static String level(int score) {
        return score >= HIGH ? "high" : score >= MEDIUM ? "medium" : "low";
    }

    private List<Base> bases(Long tenantId, Long customerId) {
        return jdbc.query("SELECT c.id, c.company_name, c.owner_id, u.full_name, c.churn_risk FROM customers c LEFT JOIN users u ON u.id = c.owner_id "
                        + "WHERE c.tenant_id = ? AND c.status = 'active'" + (customerId != null ? " AND c.id = ?" : ""),
                (rs, i) -> new Base(rs.getLong(1), rs.getString(2), (Long) rs.getObject(3), rs.getString(4), rs.getString(5)),
                customerId != null ? new Object[]{tenantId, customerId} : new Object[]{tenantId});
    }

    /** customer_id → the next {@code n} numeric columns. */
    private Map<Long, long[]> longs(String sql, int n, Object... args) {
        Map<Long, long[]> out = new HashMap<>();
        jdbc.query(sql, rs -> {
            long[] v = new long[n];
            for (int i = 0; i < n; i++) {
                v[i] = rs.getLong(i + 2);
            }
            out.put(rs.getLong(1), v);
        }, args);
        return out;
    }

    // ==================== the flag ====================

    private void flag(Long tenantId, ChurnRisk r) {
        Set<Long> ids = new LinkedHashSet<>();
        if (r.ownerId() != null) {
            ids.add(r.ownerId());
            ids.addAll(jdbc.queryForList("SELECT t.manager_id FROM users u JOIN sales_teams t ON t.id = u.team_id "
                    + "WHERE u.id = ? AND t.manager_id IS NOT NULL", Long.class, r.ownerId()));
        }
        List<User> recipients = userRepository.findAllById(ids).stream().filter(u -> u.getStatus() == AccountStatus.ACTIVE).toList();
        if (recipients.isEmpty()) {
            recipients = userRepository.findByTenantId(tenantId).stream()
                    .filter(u -> u.getRole().getName() == RoleType.ADMIN && u.getStatus() == AccountStatus.ACTIVE).toList();
        }
        String why = r.factors().stream().limit(2).map(ChurnFactor::detail).collect(Collectors.joining("; "));
        staffNotifier.notify(tenantId, NotificationEvent.CUSTOMER_CHURN_RISK, recipients, "Customer at risk: " + r.customerName(),
                "Churn risk high (" + r.score() + "/100): " + why + ".", "customer", r.customerId(), null, "/customers/" + r.customerId());
    }

    // ==================== stored reasons: one "points|name|detail" per line ====================

    private static String format(List<ChurnFactor> factors) {
        return factors.stream().map(f -> f.points() + "|" + f.name() + "|" + f.detail().replace('\n', ' ')).collect(Collectors.joining("\n"));
    }

    private static List<ChurnFactor> parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split("\n")).map(l -> l.split("\\|", 3)).filter(p -> p.length == 3)
                .map(p -> new ChurnFactor(p[1], Integer.parseInt(p[0]), p[2])).toList();
    }
}
