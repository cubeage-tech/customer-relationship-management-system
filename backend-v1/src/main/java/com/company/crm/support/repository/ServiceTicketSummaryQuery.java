package com.company.crm.support.repository;

import com.company.crm.common.enums.TicketStatus;
import com.company.crm.support.dto.response.ServiceTicketSummaryDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * One native PostgreSQL aggregate (COUNT/AVG ... FILTER) over the tenant's tickets. Native
 * because the SLA "at risk" rule needs interval arithmetic JPQL can't express. The rules mirror
 * ServiceTicketMapper#slaStatus exactly: breached = past the deadline; at risk = remaining time
 * at most a quarter of the SLA window.
 */
@Repository
public class ServiceTicketSummaryQuery {

    private static final List<String> OPEN_STATUSES = EnumSet.of(TicketStatus.OPEN, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS)
            .stream().map(TicketStatus::getDbValue).toList();

    @PersistenceContext
    private EntityManager entityManager;

    /** @param technicianId when non-null, only tickets assigned to this technician (service agent scope). */
    public ServiceTicketSummaryDto summarize(Long tenantId, Long technicianId, LocalDateTime now,
                                             LocalDateTime weekStart, LocalDateTime resolutionSince) {
        String sql = """
                SELECT
                  count(*) FILTER (WHERE status IN (:open)) AS open_count,
                  count(*) FILTER (WHERE status IN (:open) AND sla_due_at < :now) AS breached,
                  count(*) FILTER (WHERE status IN (:open) AND sla_due_at >= :now
                                   AND (sla_due_at = created_at OR (sla_due_at - :now) <= (sla_due_at - created_at) / 4)) AS at_risk,
                  count(*) FILTER (WHERE status IN (:open) AND sla_due_at >= :now AND sla_due_at <= :dueBefore) AS due_24h,
                  count(*) FILTER (WHERE resolved_at >= :weekStart) AS resolved_week,
                  avg(EXTRACT(EPOCH FROM (resolved_at - created_at)) / 3600.0) FILTER (WHERE resolved_at >= :since) AS avg_hours
                FROM service_tickets
                WHERE tenant_id = :tenantId
                """ + (technicianId != null ? " AND assigned_technician_id = :technicianId" : "");

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("open", OPEN_STATUSES)
                .setParameter("now", now)
                .setParameter("dueBefore", now.plusHours(24))
                .setParameter("weekStart", weekStart)
                .setParameter("since", resolutionSince)
                .setParameter("tenantId", tenantId);
        if (technicianId != null) {
            query.setParameter("technicianId", technicianId);
        }

        Object[] row = (Object[]) query.getSingleResult();
        long open = ((Number) row[0]).longValue();
        long breached = ((Number) row[1]).longValue();
        long atRisk = ((Number) row[2]).longValue();
        return new ServiceTicketSummaryDto(
                open - breached - atRisk,
                atRisk,
                breached,
                open,
                ((Number) row[3]).longValue(),
                ((Number) row[4]).longValue(),
                row[5] == null ? null : Math.round(((Number) row[5]).doubleValue() * 10) / 10.0);
    }
}
