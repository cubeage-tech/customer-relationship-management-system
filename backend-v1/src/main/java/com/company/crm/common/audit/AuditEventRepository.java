package com.company.crm.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(Long tenantId, String entityType, Long entityId);
}
