package com.company.crm.common.audit;

import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records business actions in the audit trail. Joins the caller's transaction on purpose:
 * an action that rolls back leaves no audit record claiming it happened.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private static final int MAX_DETAILS = 1000;

    private final AuditEventRepository auditEventRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(User actor, AuditAction action, String entityType, Long entityId, String details) {
        AuditEvent event = new AuditEvent();
        event.setTenantId(actor.getTenant() != null ? actor.getTenant().getId() : null);
        event.setActorUserId(actor.getId());
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setDetails(details != null && details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details);
        auditEventRepository.save(event);

        log.info("audit action={} entity={}#{}", action, entityType, entityId);
    }

    /** An action by a user on a given tenant's records — e.g. the platform admin managing a tenant. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(User actor, Long tenantId, AuditAction action, String entityType, Long entityId, String details) {
        AuditEvent event = new AuditEvent();
        event.setTenantId(tenantId);
        event.setActorUserId(actor.getId());
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setDetails(details != null && details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details);
        auditEventRepository.save(event);

        log.info("audit action={} entity={}#{} tenant={}", action, entityType, entityId, tenantId);
    }

    /**
     * SEC-7: an action that changes nothing (e.g. a report export), so there is no business
     * transaction to join — recorded in its own.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAccess(User actor, AuditAction action, String entityType, Long entityId, String details) {
        record(actor, action, entityType, entityId, details);
    }

    /** An action taken by a scheduled job rather than a user (no actor). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSystem(Long tenantId, AuditAction action, String entityType, Long entityId, String details) {
        AuditEvent event = new AuditEvent();
        event.setTenantId(tenantId);
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setDetails(details != null && details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details);
        auditEventRepository.save(event);

        log.info("audit action={} entity={}#{} (system)", action, entityType, entityId);
    }
}
