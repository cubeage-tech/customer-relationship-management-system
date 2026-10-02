package com.company.crm.support.repository;

import com.company.crm.common.enums.TicketStatus;
import com.company.crm.support.entity.ServiceTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceTicketRepository extends JpaRepository<ServiceTicket, Long> {

    /** Tenant-scoped lookup: a record from another tenant is indistinguishable from a missing one. */
    Optional<ServiceTicket> findByIdAndTenantId(Long id, Long tenantId);

    List<ServiceTicket> findByTenantId(Long tenantId);

    List<ServiceTicket> findByTenantIdAndAssignedTechnicianId(Long tenantId, Long technicianId);

    long countByTenantIdAndStatus(Long tenantId, TicketStatus status);
}
