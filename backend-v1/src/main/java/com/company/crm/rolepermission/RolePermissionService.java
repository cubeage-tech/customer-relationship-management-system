package com.company.crm.rolepermission;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ROLE-8: per-tenant permission customisation. A tenant admin switches a role's default
 * permissions off (and on again); the web app hides what is off and {@link PermissionGuard}
 * refuses the matching API calls. Lookups are cached briefly, and dropped on change.
 */
@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    private record Cached(Set<String> revoked, Instant loadedAt) {}

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /** One role's permissions in the tenant. */
    public record RolePermissions(String role, boolean customizable, List<String> defaults, List<String> revoked, List<String> effective) {}

    public record UpdateReq(List<String> revoked) {}

    @Transactional(readOnly = true)
    public List<RolePermissions> list(User admin) {
        Long tenantId = tenantId(admin);
        return Arrays.stream(RoleType.values()).filter(r -> r != RoleType.SUPER_ADMIN)
                .map(r -> view(r, revoked(tenantId, r))).toList();
    }

    /** The caller's own permission switches (the web app applies them to the menus and buttons). */
    @Transactional(readOnly = true)
    public RolePermissions mine(User user) {
        RoleType role = user.getRole().getName();
        return view(role, user.getTenant() == null ? Set.of() : revoked(user.getTenant().getId(), role));
    }

    /** Replaces the role's switched-off permissions. Only the role's defaults can be switched off. */
    @Transactional
    public RolePermissions update(User admin, String roleName, UpdateReq req) {
        Long tenantId = tenantId(admin);
        RoleType role;
        try {
            role = RoleType.fromDbValue(roleName);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Unknown role " + roleName);
        }
        if (!PermissionCatalog.customizable(role)) {
            throw ApiException.badRequest("The " + roleName + " role's permissions can't be changed");
        }
        Set<String> wanted = new LinkedHashSet<>(req.revoked() == null ? List.of() : req.revoked());
        Set<String> defaults = PermissionCatalog.defaults(role);
        List<String> unknown = wanted.stream().filter(p -> !defaults.contains(p)).toList();
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("Not permissions of this role: " + String.join(", ", unknown));
        }
        Set<String> before = revoked(tenantId, role);
        jdbc.update("DELETE FROM tenant_role_permission_revocations WHERE tenant_id = ? AND role = ?", tenantId, role.getDbValue());
        for (String permission : wanted) {
            jdbc.update("INSERT INTO tenant_role_permission_revocations (tenant_id, role, permission, revoked_by) VALUES (?, ?, ?, ?)",
                    tenantId, role.getDbValue(), permission, admin.getId());
        }
        cache.remove(key(tenantId, role));
        List<String> off = wanted.stream().filter(p -> !before.contains(p)).toList();
        List<String> on = before.stream().filter(p -> !wanted.contains(p)).toList();
        if (!off.isEmpty() || !on.isEmpty()) {
            auditService.record(admin, AuditAction.ROLE_PERMISSIONS_CHANGED, "role", null,
                    "role=" + role.getDbValue() + (off.isEmpty() ? "" : " off=" + String.join(",", off))
                            + (on.isEmpty() ? "" : " on=" + String.join(",", on)));
        }
        return view(role, wanted);
    }

    /** Permissions switched off for the role in the tenant (cached). */
    public Set<String> revoked(Long tenantId, RoleType role) {
        if (!PermissionCatalog.customizable(role)) {
            return Set.of();
        }
        String key = key(tenantId, role);
        Cached hit = cache.get(key);
        if (hit != null && hit.loadedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return hit.revoked();
        }
        Set<String> revoked = Set.copyOf(new HashSet<>(jdbc.queryForList(
                "SELECT permission FROM tenant_role_permission_revocations WHERE tenant_id = ? AND role = ?",
                String.class, tenantId, role.getDbValue())));
        cache.put(key, new Cached(revoked, Instant.now()));
        return revoked;
    }

    private static RolePermissions view(RoleType role, Set<String> revoked) {
        List<String> defaults = new ArrayList<>(PermissionCatalog.defaults(role));
        List<String> off = defaults.stream().filter(revoked::contains).toList();
        List<String> effective = defaults.stream().filter(p -> !revoked.contains(p)).toList();
        return new RolePermissions(role.getDbValue(), PermissionCatalog.customizable(role), defaults, off, effective);
    }

    private static String key(Long tenantId, RoleType role) {
        return tenantId + ":" + role.name();
    }

    private static Long tenantId(User user) {
        if (user.getTenant() == null) {
            throw ApiException.forbidden("Role permissions are per tenant");
        }
        return user.getTenant().getId();
    }
}
