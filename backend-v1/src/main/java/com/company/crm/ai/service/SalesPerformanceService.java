package com.company.crm.ai.service;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.salesforce.service.SalesForceScope;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * FR-AI-8.1: each salesperson's activity and conversion, analysed. For a period (default the last 90
 * days): calls (and how many connected), messages sent, meetings, visits, quotations sent and tasks
 * done or overdue; leads received and converted, deals won and lost (win rate, revenue, average deal,
 * days to close, the most common loss reason), quotations accepted, open pipeline and progress on
 * the revenue target. Each is set against the median of the tenant's sales team and the previous
 * period of the same length, and turned into plain strengths and gaps. Worked out inside the CRM —
 * nothing goes to an AI provider. Who sees whom follows the sales force scope (FR-8): a salesperson
 * sees themselves, a manager their team, admins and the owner everyone; medians are team-wide figures.
 */
@Service
public class SalesPerformanceService {

    private static final Set<RoleType> SALES_ROLES = Set.of(RoleType.SALES_EXECUTIVE, RoleType.SALES_MANAGER);
    private static final int MAX_DAYS = 366;
    /** Below this many deals decided, win rate isn't compared. */
    private static final int MIN_DEALS = 3;

    public record Metrics(long calls, long connectedCalls, long messagesSent, long meetings, long visits, long quotationsSent,
                          long tasksDone, long tasksOverdue, long touches,
                          long leadsReceived, long leadsConverted, Double leadConversionRate,
                          long dealsWon, long dealsLost, Double winRate, BigDecimal revenueWon, BigDecimal averageDeal, Double daysToClose,
                          Double quoteAcceptance, BigDecimal openPipeline, String topLossReason, Double targetAttainment, Double targetPace) {}

    /** {@code kind}: strength | gap. */
    public record Finding(String kind, String metric, String text) {}

    public record SalespersonAnalysis(Long userId, String name, String role, Metrics metrics, Metrics previous, List<Finding> strengths,
                                      List<Finding> gaps) {}

    /** Team medians of the comparable figures. */
    public record TeamBenchmark(int people, double touches, double callConnectRate, Double leadConversionRate, Double winRate,
                                BigDecimal revenueWon, Double daysToClose, Double quoteAcceptance) {}

    public record PerformanceReport(LocalDate from, LocalDate to, TeamBenchmark team, List<SalespersonAnalysis> people) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final SalesForceScope scope;
    private final UserRepository userRepository;

    public SalesPerformanceService(org.springframework.jdbc.core.JdbcTemplate jdbc, SalesForceScope scope, UserRepository userRepository) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
        this.scope = scope;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PerformanceReport analyse(User user, Long userId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(89);
        if (end.isBefore(start)) {
            throw ApiException.badRequest("The period must end on or after its start");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
            throw ApiException.badRequest("The period can be at most a year");
        }
        Long tenantId = scope.tenantId(user);
        if (userId != null && !scope.canSee(user, userId)) {
            throw ApiException.forbidden("You can only see your own or your team's performance");
        }
        List<User> team = userRepository.findByTenantId(tenantId).stream()
                .filter(u -> u.getStatus() == AccountStatus.ACTIVE && SALES_ROLES.contains(u.getRole().getName())).toList();
        List<User> shown = scope.visibleUsers(user).stream()
                .filter(u -> SALES_ROLES.contains(u.getRole().getName()) && (userId == null || u.getId().equals(userId)))
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER)).toList();

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        Map<Long, Metrics> now = metrics(tenantId, ids(team, shown), start, end);
        Map<Long, Metrics> before = metrics(tenantId, ids(List.of(), shown), start.minusDays(days), start.minusDays(1));
        TeamBenchmark bench = benchmark(team.stream().map(u -> now.get(u.getId())).filter(Objects::nonNull).toList());

        List<SalespersonAnalysis> people = shown.stream().map(u -> {
            Metrics m = now.get(u.getId());
            Metrics p = before.get(u.getId());
            List<Finding> findings = findings(m, p, bench);
            return new SalespersonAnalysis(u.getId(), u.getFullName(), u.getRole().getName().name().toLowerCase(), m, p,
                    findings.stream().filter(f -> "strength".equals(f.kind())).toList(), findings.stream().filter(f -> "gap".equals(f.kind())).toList());
        }).toList();
        return new PerformanceReport(start, end, bench, people);
    }

    // ==================== the figures ====================

    private static List<Long> ids(List<User> team, List<User> shown) {
        Set<Long> all = new java.util.LinkedHashSet<>();
        team.forEach(u -> all.add(u.getId()));
        shown.forEach(u -> all.add(u.getId()));
        return new ArrayList<>(all);
    }

    private Map<Long, Metrics> metrics(Long tenantId, Collection<Long> users, LocalDate from, LocalDate to) {
        Map<Long, Metrics> out = new HashMap<>();
        if (users.isEmpty()) {
            return out;
        }
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("t", tenantId).addValue("users", users)
                .addValue("from", Timestamp.valueOf(from.atStartOfDay())).addValue("to", Timestamp.valueOf(to.plusDays(1).atStartOfDay()))
                .addValue("today", java.sql.Date.valueOf(LocalDate.now()));
        Map<Long, long[]> calls = longs("SELECT created_by, count(*), count(*) FILTER (WHERE call_outcome = 'connected') FROM communication_logs "
                + "WHERE tenant_id = :t AND channel = 'call' AND created_by IN (:users) AND occurred_at >= :from AND occurred_at < :to GROUP BY 1", 2, p);
        Map<Long, long[]> messages = longs("SELECT created_by, count(*) FROM communication_logs WHERE tenant_id = :t AND channel IN ('email', 'whatsapp', 'sms') "
                + "AND direction = 'outbound' AND created_by IN (:users) AND occurred_at >= :from AND occurred_at < :to GROUP BY 1", 1, p);
        Map<Long, long[]> meetings = longs("SELECT organizer_id, count(*) FROM meetings WHERE tenant_id = :t AND status = 'scheduled' "
                + "AND organizer_id IN (:users) AND starts_at >= :from AND starts_at < :to GROUP BY 1", 1, p);
        Map<Long, long[]> visits = longs("SELECT user_id, count(*) FROM sales_activities WHERE tenant_id = :t AND type = 'visit' "
                + "AND user_id IN (:users) AND occurred_at >= :from AND occurred_at < :to GROUP BY 1", 1, p);
        Map<Long, long[]> quotes = longs("SELECT owner_id, count(*) FILTER (WHERE sent_at >= :from AND sent_at < :to), "
                + "count(*) FILTER (WHERE status = 'approved' AND coalesce(customer_approved_at, customer_responded_at) >= :from AND coalesce(customer_approved_at, customer_responded_at) < :to), "
                + "count(*) FILTER (WHERE status = 'rejected' AND coalesce(customer_responded_at, updated_at) >= :from AND coalesce(customer_responded_at, updated_at) < :to) "
                + "FROM quotations WHERE tenant_id = :t AND owner_id IN (:users) GROUP BY 1", 3, p);
        Map<Long, long[]> tasks = longs("SELECT assignee_id, count(*) FILTER (WHERE status = 'done' AND completed_at >= :from AND completed_at < :to), "
                + "count(*) FILTER (WHERE status = 'open' AND due_date < :today) FROM follow_up_tasks WHERE tenant_id = :t AND assignee_id IN (:users) GROUP BY 1", 2, p);
        Map<Long, long[]> leads = longs("SELECT l.owner_id, count(*), count(*) FILTER (WHERE l.stage = 'converted') FROM leads l "
                + "WHERE l.tenant_id = :t AND l.owner_id IN (:users) AND l.created_at >= :from AND l.created_at < :to GROUP BY 1", 2, p);
        // deals decided in the period: won, lost, revenue (whole units), days from creation to winning
        Map<Long, long[]> deals = longs("SELECT owner_id, count(*) FILTER (WHERE stage = 'won'), count(*) FILTER (WHERE stage = 'lost'), "
                + "coalesce(round(sum(deal_value) FILTER (WHERE stage = 'won') * 100), 0)::bigint, "
                + "coalesce(round(avg(extract(epoch FROM stage_changed_at - created_at) / 86400) FILTER (WHERE stage = 'won') * 10), -1)::bigint "
                + "FROM opportunities WHERE tenant_id = :t AND owner_id IN (:users) AND stage IN ('won', 'lost') "
                + "AND stage_changed_at >= :from AND stage_changed_at < :to GROUP BY 1", 4, p);
        Map<Long, long[]> pipeline = longs("SELECT owner_id, coalesce(round(sum(deal_value) * 100), 0)::bigint FROM opportunities "
                + "WHERE tenant_id = :t AND owner_id IN (:users) AND stage NOT IN ('won', 'lost') GROUP BY 1", 1, p);
        Map<Long, String> lossReasons = new HashMap<>();
        jdbc.query("SELECT DISTINCT ON (owner_id) owner_id, loss_reason FROM (SELECT owner_id, lower(trim(loss_reason)) AS loss_reason, count(*) AS n "
                + "FROM opportunities WHERE tenant_id = :t AND owner_id IN (:users) AND stage = 'lost' AND loss_reason IS NOT NULL AND trim(loss_reason) <> '' "
                + "AND stage_changed_at >= :from AND stage_changed_at < :to GROUP BY 1, 2) r ORDER BY owner_id, n DESC, loss_reason", p,
                rs -> { lossReasons.put(rs.getLong(1), rs.getString(2)); });
        Map<Long, double[]> targets = targets(tenantId, users, to);

        for (Long id : users) {
            long[] c = calls.getOrDefault(id, new long[2]);
            long msg = messages.getOrDefault(id, new long[1])[0];
            long meet = meetings.getOrDefault(id, new long[1])[0];
            long vis = visits.getOrDefault(id, new long[1])[0];
            long[] q = quotes.getOrDefault(id, new long[3]);
            long[] tk = tasks.getOrDefault(id, new long[2]);
            long[] ld = leads.getOrDefault(id, new long[2]);
            long[] d = deals.getOrDefault(id, new long[]{0, 0, 0, -1});
            BigDecimal revenue = cents(d[2]);
            double[] target = targets.get(id);
            out.put(id, new Metrics(c[0], c[1], msg, meet, vis, q[0], tk[0], tk[1], c[0] + msg + meet + vis,
                    ld[0], ld[1], rate(ld[1], ld[0]), d[0], d[1], rate(d[0], d[0] + d[1]), revenue,
                    d[0] == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(d[0]), 2, RoundingMode.HALF_UP),
                    d[3] < 0 ? null : d[3] / 10.0, rate(q[1], q[1] + q[2]), cents(pipeline.getOrDefault(id, new long[1])[0]),
                    lossReasons.get(id), target == null ? null : round(target[0]), target == null ? null : round(target[1])));
        }
        return out;
    }

    /** The revenue target running on {@code day}: share achieved so far, and the share of its time gone. */
    private Map<Long, double[]> targets(Long tenantId, Collection<Long> users, LocalDate day) {
        Map<Long, double[]> out = new HashMap<>();
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("t", tenantId).addValue("users", users).addValue("day", java.sql.Date.valueOf(day));
        jdbc.query("SELECT s.user_id, s.target_value, s.period_start, s.period_end, "
                + "(SELECT coalesce(sum(o.deal_value), 0) FROM opportunities o WHERE o.tenant_id = s.tenant_id AND o.owner_id = s.user_id AND o.stage = 'won' "
                + " AND o.stage_changed_at >= s.period_start AND o.stage_changed_at < s.period_end + 1) AS won "
                + "FROM sales_targets s WHERE s.tenant_id = :t AND s.user_id IN (:users) AND s.metric = 'revenue' "
                + "AND s.period_start <= :day AND s.period_end >= :day ORDER BY s.period_end", p, rs -> {
            LocalDate s = rs.getDate("period_start").toLocalDate();
            LocalDate e = rs.getDate("period_end").toLocalDate();
            double total = ChronoUnit.DAYS.between(s, e) + 1;
            double gone = Math.min(total, ChronoUnit.DAYS.between(s, day) + 1);
            out.putIfAbsent(rs.getLong("user_id"), new double[]{rs.getBigDecimal("won").doubleValue() / rs.getBigDecimal("target_value").doubleValue(), gone / total});
        });
        return out;
    }

    private Map<Long, long[]> longs(String sql, int n, MapSqlParameterSource p) {
        Map<Long, long[]> out = new HashMap<>();
        jdbc.query(sql, p, rs -> {
            long[] v = new long[n];
            for (int i = 0; i < n; i++) {
                v[i] = rs.getLong(i + 2);
            }
            out.put(rs.getLong(1), v);
        });
        return out;
    }

    // ==================== comparing ====================

    private static TeamBenchmark benchmark(List<Metrics> team) {
        return new TeamBenchmark(team.size(),
                median(team, m -> (double) m.touches()),
                median(team.stream().filter(m -> m.calls() >= 5).toList(), m -> (double) m.connectedCalls() / m.calls()),
                medianOrNull(team.stream().filter(m -> m.leadsReceived() >= MIN_DEALS).toList(), Metrics::leadConversionRate),
                medianOrNull(team.stream().filter(m -> m.dealsWon() + m.dealsLost() >= MIN_DEALS).toList(), Metrics::winRate),
                BigDecimal.valueOf(median(team, m -> m.revenueWon().doubleValue())).setScale(2, RoundingMode.HALF_UP),
                medianOrNull(team.stream().filter(m -> m.daysToClose() != null).toList(), Metrics::daysToClose),
                medianOrNull(team.stream().filter(m -> m.quoteAcceptance() != null).toList(), Metrics::quoteAcceptance));
    }

    /** Rule-based reading of one person's figures against the team and their previous period. */
    static List<Finding> findings(Metrics m, Metrics prev, TeamBenchmark t) {
        List<Finding> f = new ArrayList<>();
        if (t.people() >= 2 && t.touches() > 0) {
            if (m.touches() <= t.touches() * 0.7) {
                f.add(gap("activity", "Fewer customer touches than the team: " + m.touches() + " calls, messages, meetings and visits against a median of "
                        + fmt(t.touches()) + "."));
            } else if (m.touches() >= t.touches() * 1.3) {
                f.add(strength("activity", "More active than the team: " + m.touches() + " customer touches against a median of " + fmt(t.touches()) + "."));
            }
        }
        if (m.calls() >= 5) {
            double connect = (double) m.connectedCalls() / m.calls();
            if (connect < 0.4) {
                f.add(gap("calls", "Only " + pct(connect) + " of calls connect — try the best contact times shown on each customer and lead."));
            }
        }
        if (m.meetings() >= 3 && m.quotationsSent() == 0) {
            f.add(gap("quotations", m.meetings() + " meetings but no quotation sent — move interested customers to an offer."));
        }
        if (m.leadsReceived() >= MIN_DEALS && t.leadConversionRate() != null) {
            double diff = m.leadConversionRate() - t.leadConversionRate();
            if (diff <= -0.15) {
                f.add(gap("lead_conversion", "Converts " + pct(m.leadConversionRate()) + " of new leads against the team's " + pct(t.leadConversionRate()) + "."));
            } else if (diff >= 0.15) {
                f.add(strength("lead_conversion", "Converts " + pct(m.leadConversionRate()) + " of new leads — above the team's " + pct(t.leadConversionRate()) + "."));
            }
        }
        if (m.dealsWon() + m.dealsLost() >= MIN_DEALS && t.winRate() != null) {
            double diff = m.winRate() - t.winRate();
            if (diff <= -0.15) {
                f.add(gap("win_rate", "Wins " + pct(m.winRate()) + " of deals against the team's " + pct(t.winRate())
                        + (m.topLossReason() != null ? "; most often lost because of \"" + m.topLossReason() + "\"" : "") + "."));
            } else if (diff >= 0.15) {
                f.add(strength("win_rate", "Wins " + pct(m.winRate()) + " of deals — well above the team's " + pct(t.winRate()) + "."));
            }
        }
        if (m.daysToClose() != null && t.daysToClose() != null && t.daysToClose() > 0) {
            if (m.daysToClose() >= t.daysToClose() * 1.5) {
                f.add(gap("cycle", "Deals take " + fmt(m.daysToClose()) + " days to win against the team's " + fmt(t.daysToClose()) + "."));
            } else if (m.daysToClose() <= t.daysToClose() * 0.67) {
                f.add(strength("cycle", "Closes fast: " + fmt(m.daysToClose()) + " days to win against the team's " + fmt(t.daysToClose()) + "."));
            }
        }
        if (m.quoteAcceptance() != null && t.quoteAcceptance() != null && m.quoteAcceptance() <= t.quoteAcceptance() - 0.2) {
            f.add(gap("quote_acceptance", "Customers accept " + pct(m.quoteAcceptance()) + " of quotations against the team's " + pct(t.quoteAcceptance())
                    + " — check pricing and discounts with the price suggestions."));
        }
        if (m.tasksOverdue() >= 3) {
            f.add(gap("follow_up", m.tasksOverdue() + " follow-up tasks are overdue."));
        }
        if (prev != null && prev.revenueWon().signum() > 0) {
            double change = m.revenueWon().doubleValue() / prev.revenueWon().doubleValue() - 1;
            if (change <= -0.25) {
                f.add(gap("trend", "Revenue won is down " + pct(-change) + " on the previous period."));
            } else if (change >= 0.25) {
                f.add(strength("trend", "Revenue won is up " + pct(change) + " on the previous period."));
            }
        }
        if (m.targetAttainment() != null && m.targetPace() != null) {
            if (m.targetAttainment() >= 1) {
                f.add(strength("target", "Revenue target reached (" + pct(m.targetAttainment()) + ")."));
            } else if (m.targetAttainment() < m.targetPace() - 0.2) {
                f.add(gap("target", "Behind target: " + pct(m.targetAttainment()) + " achieved with " + pct(m.targetPace()) + " of the period gone."));
            }
        }
        return f;
    }

    // ==================== helpers ====================

    private static Finding gap(String metric, String text) {
        return new Finding("gap", metric, text);
    }

    private static Finding strength(String metric, String text) {
        return new Finding("strength", metric, text);
    }

    private static Double rate(long part, long whole) {
        return whole == 0 ? null : round((double) part / whole);
    }

    private static Double round(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    private static BigDecimal cents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private static double median(List<Metrics> items, Function<Metrics, Double> value) {
        Double m = medianOrNull(items, value);
        return m == null ? 0 : m;
    }

    private static Double medianOrNull(List<Metrics> items, Function<Metrics, Double> value) {
        List<Double> v = items.stream().map(value).filter(Objects::nonNull).sorted().toList();
        if (v.isEmpty()) {
            return null;
        }
        int n = v.size();
        return round(n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2);
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
