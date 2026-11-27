package com.company.crm.common.audit;

import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NFR-7.2d: reading the audit trail. A tenant admin or executive owner sees their own tenant's
 * events; the platform super admin sees every tenant's (optionally one). Newest first, paged.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_PAGE = 200;

    @PersistenceContext
    private EntityManager entityManager;

    public record Filters(Long tenantId, String entityType, String action, Long actorId, Long entityId,
                          LocalDate from, LocalDate to, int page, int size) {}

    public record Item(Long id, LocalDateTime createdAt, Long tenantId, String tenantName, Long actorId, String actorName,
                       String action, String entityType, Long entityId, String details) {}

    public record Page(List<Item> items, long total, int page, int size, List<String> actions) {}

    @Transactional(readOnly = true)
    public Page search(User viewer, Filters f) {
        Long tenantId;
        if (viewer.getRole().getName() == RoleType.SUPER_ADMIN) {
            tenantId = f.tenantId(); // null = all tenants
        } else {
            if (viewer.getTenant() == null) {
                throw ApiException.forbidden("The audit log is per tenant");
            }
            if (f.tenantId() != null && !f.tenantId().equals(viewer.getTenant().getId())) {
                throw ApiException.forbidden("You can only see your own company's audit log");
            }
            tenantId = viewer.getTenant().getId();
        }
        if (f.action() != null && !f.action().isBlank()
                && Arrays.stream(AuditAction.values()).noneMatch(a -> a.name().equals(f.action()))) {
            throw ApiException.badRequest("Unknown action");
        }
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
            throw ApiException.badRequest("The period must end on or after its start");
        }
        int size = Math.min(Math.max(f.size(), 1), MAX_PAGE);
        int page = Math.max(f.page(), 0);

        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (tenantId != null) {
            where.append(" AND e.tenant_id = :tenantId");
            params.put("tenantId", tenantId);
        }
        if (f.entityType() != null && !f.entityType().isBlank()) {
            where.append(" AND e.entity_type = :entityType");
            params.put("entityType", f.entityType().trim());
        }
        if (f.action() != null && !f.action().isBlank()) {
            where.append(" AND e.action = :action");
            params.put("action", f.action());
        }
        if (f.actorId() != null) {
            where.append(" AND e.actor_user_id = :actorId");
            params.put("actorId", f.actorId());
        }
        if (f.entityId() != null) {
            where.append(" AND e.entity_id = :entityId");
            params.put("entityId", f.entityId());
        }
        if (f.from() != null) {
            where.append(" AND e.created_at >= :from");
            params.put("from", f.from().atStartOfDay());
        }
        if (f.to() != null) {
            where.append(" AND e.created_at < :to");
            params.put("to", f.to().plusDays(1).atStartOfDay());
        }

        Query count = entityManager.createNativeQuery("SELECT count(*) FROM audit_events e" + where);
        Query rows = entityManager.createNativeQuery("SELECT e.id, e.created_at, e.tenant_id, t.company_name, e.actor_user_id, u.full_name,"
                + " e.action, e.entity_type, e.entity_id, e.details FROM audit_events e"
                + " LEFT JOIN tenants t ON t.id = e.tenant_id LEFT JOIN users u ON u.id = e.actor_user_id"
                + where + " ORDER BY e.created_at DESC, e.id DESC");
        params.forEach((k, v) -> {
            count.setParameter(k, v);
            rows.setParameter(k, v);
        });
        rows.setFirstResult(page * size).setMaxResults(size);

        @SuppressWarnings("unchecked")
        List<Object[]> result = rows.getResultList();
        List<Item> items = result.stream().map(r -> new Item(
                num(r[0]), toDateTime(r[1]), num(r[2]), (String) r[3], num(r[4]),
                r[4] == null ? "System" : (String) r[5], (String) r[6], (String) r[7], num(r[8]), (String) r[9])).toList();
        return new Page(items, ((Number) count.getSingleResult()).longValue(), page, size,
                Arrays.stream(AuditAction.values()).map(Enum::name).toList());
    }

    private static Long num(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static LocalDateTime toDateTime(Object v) {
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return v instanceof LocalDateTime ldt ? ldt : null;
    }
}
