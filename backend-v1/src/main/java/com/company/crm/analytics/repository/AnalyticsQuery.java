package com.company.crm.analytics.repository;

import com.company.crm.analytics.service.ReportScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-9.1–9.5: report aggregates in the database, always tenant-scoped and narrowed by
 * {@link ReportScope} (owners, industry, period). Revenue is the value of deals won in the period.
 */
@Repository
public class AnalyticsQuery {

    @PersistenceContext
    private EntityManager entityManager;

    /** A label with a count and a value (value may be zero). */
    public record Bucket(String key, long count, BigDecimal value) {}

    // ==================== FR-9.1 headline ====================

    /** [leads created, of which converted] in the period. */
    public long[] leads(ReportScope s) {
        Object[] row = (Object[]) query("SELECT COUNT(*), COUNT(*) FILTER (WHERE l.stage = 'converted') FROM leads l"
                + " WHERE l.tenant_id = :tenantId AND l.created_at >= :start AND l.created_at < :end"
                + owners(s, "l.owner_id") + (s.industry() != null ? " AND l.industry = :industry" : ""), s).getSingleResult();
        return new long[]{num(row[0]), num(row[1])};
    }

    /** Value and count of deals won in the period. */
    public Bucket won(ReportScope s) {
        Object[] row = (Object[]) query("SELECT COALESCE(SUM(o.deal_value), 0), COUNT(*)" + WON_FROM + wonWhere(s), s).getSingleResult();
        return new Bucket("won", num(row[1]), dec(row[0]));
    }

    /** Value and count of open deals right now (not won or lost). */
    public Bucket openPipeline(ReportScope s) {
        Object[] row = (Object[]) query("SELECT COALESCE(SUM(o.deal_value), 0), COUNT(*) FROM opportunities o JOIN customers c ON c.id = o.customer_id"
                + " WHERE o.tenant_id = :tenantId AND o.stage NOT IN ('won', 'lost')" + owners(s, "o.owner_id") + industry(s), s)
                .getSingleResult();
        return new Bucket("open", num(row[1]), dec(row[0]));
    }

    // ==================== FR-9.2 sales ====================

    /** Opportunities created in the period, by current stage. */
    public List<Bucket> opportunitiesByStage(ReportScope s) {
        return buckets("SELECT o.stage, COUNT(*), COALESCE(SUM(o.deal_value), 0) FROM opportunities o JOIN customers c ON c.id = o.customer_id"
                + " WHERE o.tenant_id = :tenantId AND o.created_at >= :start AND o.created_at < :end" + owners(s, "o.owner_id") + industry(s)
                + " GROUP BY o.stage", s);
    }

    /** Opportunities created in the period, by owner and stage: rows [ownerId, ownerName, stage, count, value]. */
    @SuppressWarnings("unchecked")
    public List<Object[]> opportunitiesByOwnerAndStage(ReportScope s) {
        return query("SELECT o.owner_id, COALESCE(u.full_name, 'Unassigned'), o.stage, COUNT(*), COALESCE(SUM(o.deal_value), 0)"
                + " FROM opportunities o JOIN customers c ON c.id = o.customer_id LEFT JOIN users u ON u.id = o.owner_id"
                + " WHERE o.tenant_id = :tenantId AND o.created_at >= :start AND o.created_at < :end" + owners(s, "o.owner_id") + industry(s)
                + " GROUP BY o.owner_id, u.full_name, o.stage ORDER BY 2", s).getResultList();
    }

    // ==================== FR-9.3 customers ====================

    /** [active customers, created in the period]. */
    public long[] customerCounts(ReportScope s) {
        Object[] row = (Object[]) query("SELECT COUNT(*) FILTER (WHERE c.status = 'active'),"
                + " COUNT(*) FILTER (WHERE c.created_at >= :start AND c.created_at < :end)"
                + " FROM customers c WHERE c.tenant_id = :tenantId" + owners(s, "c.owner_id") + industry(s), s).getSingleResult();
        return new long[]{num(row[0]), num(row[1])};
    }

    public List<Bucket> customersByIndustry(ReportScope s) {
        return buckets("SELECT c.industry, COUNT(*), 0 FROM customers c WHERE c.tenant_id = :tenantId AND c.status = 'active'"
                + owners(s, "c.owner_id") + industry(s) + " GROUP BY c.industry ORDER BY 2 DESC", s);
    }

    /** Communications in the period with the scoped customers, by channel. */
    public List<Bucket> activityByChannel(ReportScope s) {
        return buckets("SELECT m.channel, COUNT(*), 0 FROM communication_logs m JOIN customers c ON c.id = m.customer_id"
                + " WHERE m.tenant_id = :tenantId AND m.occurred_at >= :start AND m.occurred_at < :end" + owners(s, "c.owner_id") + industry(s)
                + " GROUP BY m.channel ORDER BY 2 DESC", s);
    }

    /** Active scoped customers with at least one communication in the period. */
    public long customersContacted(ReportScope s) {
        return num(query("SELECT COUNT(DISTINCT c.id) FROM customers c JOIN communication_logs m ON m.customer_id = c.id"
                + " WHERE c.tenant_id = :tenantId AND c.status = 'active' AND m.occurred_at >= :start AND m.occurred_at < :end"
                + owners(s, "c.owner_id") + industry(s), s).getSingleResult());
    }

    // ==================== FR-9.4 revenue ====================

    public List<Bucket> revenueByMonth(ReportScope s) {
        return buckets("SELECT to_char(date_trunc('month', o.stage_changed_at), 'YYYY-MM'), COUNT(*), COALESCE(SUM(o.deal_value), 0)"
                + WON_FROM + wonWhere(s) + " GROUP BY 1 ORDER BY 1", s);
    }

    public List<Bucket> revenueByIndustry(ReportScope s) {
        return buckets("SELECT c.industry, COUNT(*), COALESCE(SUM(o.deal_value), 0)" + WON_FROM + wonWhere(s) + " GROUP BY c.industry ORDER BY 3 DESC", s);
    }

    public List<Bucket> revenueByProduct(ReportScope s) {
        return buckets("SELECT COALESCE(NULLIF(TRIM(o.product_service), ''), '(not specified)'), COUNT(*), COALESCE(SUM(o.deal_value), 0)"
                + WON_FROM + wonWhere(s) + " GROUP BY 1 ORDER BY 3 DESC", s);
    }

    /** Rows [ownerId, ownerName, count, value]. */
    @SuppressWarnings("unchecked")
    public List<Object[]> revenueBySalesperson(ReportScope s) {
        return query("SELECT o.owner_id, COALESCE(u.full_name, 'Unassigned'), COUNT(*), COALESCE(SUM(o.deal_value), 0)"
                + WON_FROM + " LEFT JOIN users u ON u.id = o.owner_id" + wonWhere(s) + " GROUP BY o.owner_id, u.full_name ORDER BY 4 DESC", s)
                .getResultList();
    }

    // ==================== FR-9.5 team performance ====================

    /** Per owner: [count, value] of deals won in the period. */
    public Map<Long, Bucket> wonByOwner(ReportScope s) {
        Map<Long, Bucket> out = new HashMap<>();
        for (Object[] r : revenueBySalesperson(s)) {
            if (r[0] != null) {
                out.put(num(r[0]), new Bucket(String.valueOf(r[1]), num(r[2]), dec(r[3])));
            }
        }
        return out;
    }

    /** Per user: commission (not void) on deals won in the period. */
    @SuppressWarnings("unchecked")
    public Map<Long, BigDecimal> commissionByUser(ReportScope s) {
        List<Object[]> rows = query("SELECT e.user_id, COALESCE(SUM(e.amount), 0) FROM commission_entries e"
                + " JOIN opportunities o ON o.id = e.opportunity_id JOIN customers c ON c.id = o.customer_id"
                + " WHERE e.tenant_id = :tenantId AND e.status <> 'void' AND e.won_at >= :start AND e.won_at < :end"
                + owners(s, "e.user_id") + industry(s) + " GROUP BY e.user_id", s).getResultList();
        Map<Long, BigDecimal> out = new HashMap<>();
        rows.forEach(r -> out.put(num(r[0]), dec(r[1])));
        return out;
    }

    // ==================== Helpers ====================

    private static final String WON_FROM = " FROM opportunities o JOIN customers c ON c.id = o.customer_id";

    private static String wonWhere(ReportScope s) {
        return " WHERE o.tenant_id = :tenantId AND o.stage = 'won' AND o.stage_changed_at >= :start AND o.stage_changed_at < :end"
                + owners(s, "o.owner_id") + industry(s);
    }

    /** Owner restriction; an empty owner set matches nothing. */
    private static String owners(ReportScope s, String column) {
        if (s.ownerIds() == null) {
            return "";
        }
        return s.ownerIds().isEmpty() ? " AND 1 = 0" : " AND " + column + " IN (:owners)";
    }

    private static String industry(ReportScope s) {
        return s.industry() != null ? " AND c.industry = :industry" : "";
    }

    private Query query(String sql, ReportScope s) {
        Query q = entityManager.createNativeQuery(sql).setParameter("tenantId", s.tenantId());
        if (sql.contains(":start")) {
            q.setParameter("start", s.start()).setParameter("end", s.end());
        }
        if (sql.contains(":owners")) {
            q.setParameter("owners", s.ownerIds());
        }
        if (sql.contains(":industry")) {
            q.setParameter("industry", s.industry());
        }
        return q;
    }

    @SuppressWarnings("unchecked")
    private List<Bucket> buckets(String sql, ReportScope s) {
        return ((List<Object[]>) query(sql, s).getResultList()).stream()
                .map(r -> new Bucket(String.valueOf(r[0]), num(r[1]), dec(r[2])))
                .toList();
    }

    private static long num(Object v) {
        return v == null ? 0 : ((Number) v).longValue();
    }

    private static BigDecimal dec(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
