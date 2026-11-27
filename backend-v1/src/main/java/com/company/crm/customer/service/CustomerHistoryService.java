package com.company.crm.customer.service;

import com.company.crm.common.enums.RoleType;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.company.crm.common.enums.RoleType.*;

/**
 * API-G02 (FR-1.3/1.4): everything that happened with a customer, newest first — deals opened,
 * won and lost, quotations drafted, sent, viewed and answered, service requests raised and
 * resolved, messages and calls (no message text), meetings, documents and changes to the record.
 * Each kind is shown only to the roles that may see it; the customer itself to whoever may view it.
 */
@Service
@RequiredArgsConstructor
public class CustomerHistoryService {

    private static final int MAX_EVENTS = 500;
    private static final Set<RoleType> DEAL_ROLES = Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE, FINANCE_APPROVER, EXECUTIVE_OWNER);
    private static final Set<RoleType> TICKET_ROLES = Set.of(ADMIN, SALES_MANAGER, SERVICE_AGENT, EXECUTIVE_OWNER);
    private static final Set<RoleType> AUDIT_ROLES = Set.of(ADMIN, EXECUTIVE_OWNER);

    private final CustomerService customerService;
    private final JdbcTemplate jdbc;

    /** type: customer | deal | quotation | ticket | message | meeting | document | change. */
    public record HistoryEvent(LocalDateTime at, String type, String title, String detail, String recordType, Long recordId) {}

    @Transactional(readOnly = true)
    public List<HistoryEvent> history(User user, Long customerId, Set<String> types) {
        customerService.getCustomer(user, customerId); // the customer's own access rules (404/403)
        Long tenantId = user.getTenant().getId();
        RoleType role = user.getRole().getName();
        List<HistoryEvent> events = new ArrayList<>();
        boolean all = types == null || types.isEmpty();

        if (all || types.contains("customer")) {
            jdbc.query("SELECT created_at FROM customers WHERE id = ? AND tenant_id = ?", rs -> {
                events.add(new HistoryEvent(time(rs.getTimestamp(1)), "customer", "Customer added", null, "customer", customerId));
            }, customerId, tenantId);
        }
        if (DEAL_ROLES.contains(role) && (all || types.contains("deal"))) {
            jdbc.query("SELECT id, product_service, deal_value, stage, loss_reason, created_at, stage_changed_at FROM opportunities"
                    + " WHERE tenant_id = ? AND customer_id = ?", rs -> {
                long id = rs.getLong("id");
                String what = (rs.getString("product_service") != null ? rs.getString("product_service") + " · " : "") + money(rs.getBigDecimal("deal_value"));
                events.add(new HistoryEvent(time(rs.getTimestamp("created_at")), "deal", "Deal opened", what, "opportunity", id));
                String stage = rs.getString("stage");
                if (("won".equals(stage) || "lost".equals(stage)) && rs.getTimestamp("stage_changed_at") != null) {
                    events.add(new HistoryEvent(time(rs.getTimestamp("stage_changed_at")), "deal", "won".equals(stage) ? "Deal won" : "Deal lost",
                            what + ("lost".equals(stage) && rs.getString("loss_reason") != null ? " · " + rs.getString("loss_reason") : ""), "opportunity", id));
                }
            }, tenantId, customerId);

            jdbc.query("SELECT q.id, q.quotation_number, q.status, q.created_at, q.sent_at, q.viewed_at, q.customer_approved_at, q.updated_at,"
                    + " COALESCE((SELECT SUM(i.quantity * i.unit_price * (1 - COALESCE(i.discount_percent, 0) / 100))"
                    + " FROM quotation_line_items i WHERE i.quotation_id = q.id), 0) AS total"
                    + " FROM quotations q WHERE q.tenant_id = ? AND q.customer_id = ?", rs -> {
                long id = rs.getLong("id");
                String number = rs.getString("quotation_number");
                String total = money(rs.getBigDecimal("total"));
                events.add(new HistoryEvent(time(rs.getTimestamp("created_at")), "quotation", "Quotation " + number + " drafted", total, "quotation", id));
                if (rs.getTimestamp("sent_at") != null) {
                    events.add(new HistoryEvent(time(rs.getTimestamp("sent_at")), "quotation", "Quotation " + number + " sent", total, "quotation", id));
                }
                if (rs.getTimestamp("viewed_at") != null) {
                    events.add(new HistoryEvent(time(rs.getTimestamp("viewed_at")), "quotation", "Quotation " + number + " viewed by the customer", null, "quotation", id));
                }
                if (rs.getTimestamp("customer_approved_at") != null) {
                    events.add(new HistoryEvent(time(rs.getTimestamp("customer_approved_at")), "quotation", "Quotation " + number + " approved by the customer", total, "quotation", id));
                } else if ("rejected".equals(rs.getString("status"))) {
                    events.add(new HistoryEvent(time(rs.getTimestamp("updated_at")), "quotation", "Quotation " + number + " declined", total, "quotation", id));
                }
            }, tenantId, customerId);
        }
        if (TICKET_ROLES.contains(role) && (all || types.contains("ticket"))) {
            jdbc.query("SELECT id, subject, priority, created_at, resolved_at, feedback_score FROM service_tickets WHERE tenant_id = ? AND customer_id = ?", rs -> {
                long id = rs.getLong("id");
                events.add(new HistoryEvent(time(rs.getTimestamp("created_at")), "ticket", "Service request raised",
                        rs.getString("subject") + " · " + rs.getString("priority"), "service_ticket", id));
                if (rs.getTimestamp("resolved_at") != null) {
                    int score = rs.getInt("feedback_score");
                    boolean rated = !rs.wasNull();
                    events.add(new HistoryEvent(time(rs.getTimestamp("resolved_at")), "ticket", "Service request resolved",
                            rs.getString("subject") + (rated ? " · rated " + score + "/5" : ""), "service_ticket", id));
                }
            }, tenantId, customerId);
        }
        if (all || types.contains("message")) {
            jdbc.query("SELECT id, channel, direction, subject, call_outcome, occurred_at FROM communication_logs WHERE tenant_id = ? AND customer_id = ?"
                    + " AND channel <> 'meeting' ORDER BY occurred_at DESC LIMIT ?", rs -> {
                String channel = rs.getString("channel");
                String title = "call".equals(channel) ? ("inbound".equals(rs.getString("direction")) ? "Call received" : "Call made")
                        : label(channel) + ("inbound".equals(rs.getString("direction")) ? " received" : " sent");
                String detail = rs.getString("subject") != null ? rs.getString("subject")
                        : rs.getString("call_outcome") != null ? rs.getString("call_outcome").replace('_', ' ') : null;
                events.add(new HistoryEvent(time(rs.getTimestamp("occurred_at")), "message", title, detail, "communication", rs.getLong("id")));
            }, tenantId, customerId, MAX_EVENTS);
        }
        if (all || types.contains("meeting")) {
            jdbc.query("SELECT id, title, starts_at, status FROM meetings WHERE tenant_id = ? AND customer_id = ?", rs -> {
                events.add(new HistoryEvent(time(rs.getTimestamp("starts_at")), "meeting",
                        "cancelled".equals(rs.getString("status")) ? "Meeting cancelled" : "Meeting", rs.getString("title"), "meeting", rs.getLong("id")));
            }, tenantId, customerId);
        }
        if (all || types.contains("document")) {
            jdbc.query("SELECT id, file_name, created_at FROM customer_documents WHERE tenant_id = ? AND customer_id = ?", rs -> {
                events.add(new HistoryEvent(time(rs.getTimestamp("created_at")), "document", "Document uploaded", rs.getString("file_name"), "document", rs.getLong("id")));
            }, tenantId, customerId);
        }
        if (AUDIT_ROLES.contains(role) && (all || types.contains("change"))) {
            jdbc.query("SELECT action, details, created_at FROM audit_events WHERE tenant_id = ? AND entity_type = 'customer' AND entity_id = ?"
                    + " AND action <> 'CUSTOMER_CREATED' ORDER BY created_at DESC LIMIT ?", rs -> {
                events.add(new HistoryEvent(time(rs.getTimestamp("created_at")), "change", CHANGE_TITLES.getOrDefault(rs.getString("action"),
                        "Record changed"), rs.getString("details"), "customer", customerId));
            }, tenantId, customerId, MAX_EVENTS);
        }
        return events.stream()
                .sorted(Comparator.comparing(HistoryEvent::at, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_EVENTS)
                .toList();
    }

    private static final Map<String, String> CHANGE_TITLES = Map.of(
            "CUSTOMER_UPDATED", "Details changed",
            "CUSTOMER_ARCHIVED", "Archived",
            "CUSTOMER_RESTORED", "Restored",
            "CUSTOMER_CONSENT_CHANGED", "Communication preferences changed",
            "CUSTOM_FIELD_VALUES_UPDATED", "Industry details changed",
            "AI_EXCLUSION_CHANGED", "AI exclusion changed",
            "DATA_EXPORTED", "Personal data exported",
            "DATA_ERASED", "Personal data erased");

    private static String label(String channel) {
        return switch (channel) {
            case "whatsapp" -> "WhatsApp";
            case "sms" -> "SMS";
            default -> "Email";
        };
    }

    private static LocalDateTime time(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    private static String money(BigDecimal v) {
        return "Rs " + NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).format(v == null ? BigDecimal.ZERO : v);
    }
}
