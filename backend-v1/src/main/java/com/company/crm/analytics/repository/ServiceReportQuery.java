package com.company.crm.analytics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REP-SVC: ticket aggregates for a period. Volume counts tickets raised in the period; resolution
 * time and SLA compliance count tickets resolved in it (within SLA = resolved by the deadline);
 * feedback counts ratings given in it (staff-recorded ratings without a date count at resolution).
 */
@Repository
public class ServiceReportQuery {

    private static final String FEEDBACK_AT = "COALESCE(t.feedback_at, t.resolved_at)";

    @PersistenceContext
    private EntityManager entityManager;

    /** Filters: technicianId and priority are optional (null = all). */
    public record Filter(Long tenantId, Long technicianId, String priority, LocalDateTime start, LocalDateTime end) {}

    /**
     * Rows [key, label, created, resolved, avgHours, resolvedWithinSla, feedbackCount, avgFeedback].
     * groupBy is "priority", "technician" or null for one total row.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> aggregate(Filter f, String groupBy) {
        String key = switch (groupBy == null ? "" : groupBy) {
            case "priority" -> "t.priority, t.priority";
            case "technician" -> "CAST(t.assigned_technician_id AS VARCHAR), COALESCE(u.full_name, 'Unassigned')";
            default -> "'all', 'All'";
        };
        String sql = "SELECT " + key + ","
                + " count(*) FILTER (WHERE t.created_at >= :start AND t.created_at < :end),"
                + " count(*) FILTER (WHERE t.resolved_at >= :start AND t.resolved_at < :end),"
                + " avg(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0) FILTER (WHERE t.resolved_at >= :start AND t.resolved_at < :end),"
                + " count(*) FILTER (WHERE t.resolved_at >= :start AND t.resolved_at < :end AND t.resolved_at <= t.sla_due_at),"
                + " count(t.feedback_score) FILTER (WHERE " + FEEDBACK_AT + " >= :start AND " + FEEDBACK_AT + " < :end),"
                + " avg(t.feedback_score) FILTER (WHERE " + FEEDBACK_AT + " >= :start AND " + FEEDBACK_AT + " < :end)"
                + " FROM service_tickets t LEFT JOIN users u ON u.id = t.assigned_technician_id"
                + where(f)
                + " AND ((t.created_at >= :start AND t.created_at < :end) OR (t.resolved_at >= :start AND t.resolved_at < :end)"
                + "      OR (t.feedback_score IS NOT NULL AND " + FEEDBACK_AT + " >= :start AND " + FEEDBACK_AT + " < :end))"
                + (groupBy != null ? " GROUP BY 1, 2 ORDER BY 3 DESC, 2" : "");
        Query q = bind(entityManager.createNativeQuery(sql), f).setParameter("start", f.start()).setParameter("end", f.end());
        return q.getResultList();
    }

    /** Open tickets past their SLA deadline right now. */
    public long openBreached(Filter f, List<String> openStatuses, LocalDateTime now) {
        Query q = bind(entityManager.createNativeQuery("SELECT count(*) FROM service_tickets t" + where(f)
                + " AND t.status IN (:open) AND t.sla_due_at < :now"), f)
                .setParameter("open", openStatuses).setParameter("now", now);
        return ((Number) q.getSingleResult()).longValue();
    }

    private static String where(Filter f) {
        return " WHERE t.tenant_id = :tenantId"
                + (f.technicianId() != null ? " AND t.assigned_technician_id = :technicianId" : "")
                + (f.priority() != null ? " AND t.priority = :priority" : "");
    }

    private static Query bind(Query q, Filter f) {
        q.setParameter("tenantId", f.tenantId());
        if (f.technicianId() != null) {
            q.setParameter("technicianId", f.technicianId());
        }
        if (f.priority() != null) {
            q.setParameter("priority", f.priority());
        }
        return q;
    }
}
