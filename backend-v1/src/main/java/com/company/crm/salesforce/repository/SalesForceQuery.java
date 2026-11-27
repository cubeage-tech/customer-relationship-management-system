package com.company.crm.salesforce.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** FR-8.2 attainment and FR-8.3 activity counts, aggregated in the database. */
@Repository
public class SalesForceQuery {

    /** Won-deal totals: value and count. */
    public record WonTotals(BigDecimal value, long count) {}

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Deals won in [from, to) by one salesperson ({@code userId}) or by the members of a team
     * ({@code teamId}; current membership). Exactly one of the two is non-null.
     */
    public WonTotals wonDeals(Long tenantId, Long userId, Long teamId, LocalDateTime from, LocalDateTime to) {
        String owner = userId != null ? "o.owner_id = :who" : "o.owner_id IN (SELECT u.id FROM users u WHERE u.team_id = :who)";
        Object[] row = (Object[]) entityManager.createNativeQuery(
                        "SELECT COALESCE(SUM(o.deal_value), 0), COUNT(*) FROM opportunities o WHERE o.tenant_id = :tenantId"
                                + " AND o.stage = 'won' AND o.stage_changed_at >= :from AND o.stage_changed_at < :to AND " + owner)
                .setParameter("tenantId", tenantId)
                .setParameter("who", userId != null ? userId : teamId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return new WonTotals(new BigDecimal(row[0].toString()), ((Number) row[1]).longValue());
    }

    /** FR-8.3: per-user counts in [from, to) of one activity source (see the SQL below). */
    public Map<Long, Long> calls(Long tenantId, Collection<Long> userIds, LocalDateTime from, LocalDateTime to) {
        return countBy("SELECT created_by, COUNT(*) FROM communication_logs WHERE tenant_id = :tenantId AND channel = 'call'"
                + " AND created_by IN (:users) AND occurred_at >= :from AND occurred_at < :to GROUP BY created_by", tenantId, userIds, from, to);
    }

    public Map<Long, Long> meetings(Long tenantId, Collection<Long> userIds, LocalDateTime from, LocalDateTime to) {
        return countBy("SELECT organizer_id, COUNT(*) FROM meetings WHERE tenant_id = :tenantId AND status = 'scheduled'"
                + " AND organizer_id IN (:users) AND starts_at >= :from AND starts_at < :to GROUP BY organizer_id", tenantId, userIds, from, to);
    }

    public Map<Long, Long> quotationsSent(Long tenantId, Collection<Long> userIds, LocalDateTime from, LocalDateTime to) {
        return countBy("SELECT owner_id, COUNT(*) FROM quotations WHERE tenant_id = :tenantId AND sent_at IS NOT NULL"
                + " AND owner_id IN (:users) AND sent_at >= :from AND sent_at < :to GROUP BY owner_id", tenantId, userIds, from, to);
    }

    public Map<Long, Long> visits(Long tenantId, Collection<Long> userIds, LocalDateTime from, LocalDateTime to) {
        return countBy("SELECT user_id, COUNT(*) FROM sales_activities WHERE tenant_id = :tenantId AND type = 'visit'"
                + " AND user_id IN (:users) AND occurred_at >= :from AND occurred_at < :to GROUP BY user_id", tenantId, userIds, from, to);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Long> countBy(String sql, Long tenantId, Collection<Long> userIds, LocalDateTime from, LocalDateTime to) {
        Map<Long, Long> counts = new HashMap<>();
        if (userIds.isEmpty()) {
            return counts;
        }
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("tenantId", tenantId)
                .setParameter("users", userIds)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        for (Object[] r : rows) {
            counts.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }
        return counts;
    }
}
