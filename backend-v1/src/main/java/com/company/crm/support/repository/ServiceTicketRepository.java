package com.company.crm.support.repository;

import com.company.crm.common.enums.TicketStatus;
import com.company.crm.support.entity.ServiceTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ServiceTicketRepository extends JpaRepository<ServiceTicket, Long>, JpaSpecificationExecutor<ServiceTicket> {

    /** Tenant-scoped lookup: a record from another tenant is indistinguishable from a missing one. */
    Optional<ServiceTicket> findByIdAndTenantId(Long id, Long tenantId);

    List<ServiceTicket> findByTenantId(Long tenantId);

    List<ServiceTicket> findByTenantIdAndAssignedTechnicianId(Long tenantId, Long technicianId);

    long countByTenantIdAndStatus(Long tenantId, TicketStatus status);

    /** Filtered page, with the associations the DTO mapper reads fetched in the same query (no N+1). */
    @Override
    @EntityGraph(attributePaths = {"customer", "assignedTechnician", "asset"})
    Page<ServiceTicket> findAll(Specification<ServiceTicket> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"customer", "assignedTechnician", "asset"})
    List<ServiceTicket> findAll(Specification<ServiceTicket> spec, Sort sort);

    /**
     * Atomic claim: assigns the ticket only if nobody holds it. The database serialises
     * concurrent claims on the row, so exactly one caller gets 1 row updated; the rest get 0.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ServiceTicket t set t.assignedTechnician.id = :technicianId, t.updatedAt = :now "
            + "where t.id = :ticketId and t.tenant.id = :tenantId and t.assignedTechnician is null")
    int assignIfUnassigned(@Param("ticketId") Long ticketId, @Param("tenantId") Long tenantId,
                           @Param("technicianId") Long technicianId, @Param("now") LocalDateTime now);

    /** FR-6.5: the ticket behind a customer feedback link. */
    Optional<ServiceTicket> findByFeedbackToken(String feedbackToken);

    /** FR-6.4c: open tickets past their SLA that haven't been escalated for it. */
    @Query(value = "SELECT id FROM service_tickets WHERE status IN ('open', 'assigned', 'in_progress')"
            + " AND sla_due_at < :now AND sla_breach_notified_at IS NULL ORDER BY sla_due_at", nativeQuery = true)
    List<Long> findBreachedUnescalatedIds(@Param("now") LocalDateTime now);

    /** FR-6.4c: open tickets within the last 25% of their SLA window (same rule as the SLA flag), not yet escalated. */
    @Query(value = "SELECT id FROM service_tickets WHERE status IN ('open', 'assigned', 'in_progress')"
            + " AND sla_due_at >= :now AND sla_at_risk_notified_at IS NULL"
            + " AND (sla_due_at = created_at OR (sla_due_at - :now) <= (sla_due_at - created_at) / 4)"
            + " ORDER BY sla_due_at", nativeQuery = true)
    List<Long> findAtRiskUnescalatedIds(@Param("now") LocalDateTime now);

    /** FR-6.4c: claims the breach escalation (also suppresses a later at-risk one). 0 = already sent. */
    @Modifying
    @Query("update ServiceTicket t set t.slaBreachNotifiedAt = :now,"
            + " t.slaAtRiskNotifiedAt = coalesce(t.slaAtRiskNotifiedAt, :now)"
            + " where t.id = :id and t.slaBreachNotifiedAt is null")
    int claimBreachEscalation(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** FR-6.4c: claims the at-risk escalation. 0 = already sent. */
    @Modifying
    @Query("update ServiceTicket t set t.slaAtRiskNotifiedAt = :now where t.id = :id and t.slaAtRiskNotifiedAt is null")
    int claimAtRiskEscalation(@Param("id") Long id, @Param("now") LocalDateTime now);
}
