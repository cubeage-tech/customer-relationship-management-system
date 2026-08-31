package com.company.crm.support.repository;

import com.company.crm.support.entity.ServiceTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceTicketRepository extends JpaRepository<ServiceTicket, Long> {

    List<ServiceTicket> findByTenantId(Long tenantId);

    List<ServiceTicket> findByTenantIdAndAssignedTechnicianId(Long tenantId, Long technicianId);
}
