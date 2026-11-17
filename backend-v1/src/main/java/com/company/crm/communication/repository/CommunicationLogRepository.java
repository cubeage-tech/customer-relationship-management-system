package com.company.crm.communication.repository;

import com.company.crm.communication.entity.CommunicationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunicationLogRepository extends JpaRepository<CommunicationLog, Long> {

    List<CommunicationLog> findByTenantIdAndCustomerIdOrderByOccurredAtDescIdDesc(Long tenantId, Long customerId);
}
