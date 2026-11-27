package com.company.crm.common.scope;

import java.util.Set;

/**
 * Which owned records (customers, leads, opportunities, quotations) a user may see inside their
 * tenant: all of them, or only those owned by a fixed set of users. Tenant isolation itself is
 * always applied separately — a scope never widens beyond the user's tenant.
 */
public record DataScope(Level level, Set<Long> ownerIds) {

    public enum Level { TENANT, TEAM, OWN }

    public DataScope {
        ownerIds = ownerIds == null ? Set.of() : Set.copyOf(ownerIds);
    }

    public static DataScope tenant() {
        return new DataScope(Level.TENANT, Set.of());
    }

    public static DataScope team(Set<Long> memberIds) {
        return new DataScope(Level.TEAM, memberIds);
    }

    public static DataScope own(Long userId) {
        return new DataScope(Level.OWN, Set.of(userId));
    }

    public boolean isTenantWide() {
        return level == Level.TENANT;
    }

    /** Whether a record owned by {@code ownerId} (may be null = unassigned) is visible. */
    public boolean allows(Long ownerId) {
        return isTenantWide() || (ownerId != null && ownerIds.contains(ownerId));
    }

    /** Stable key fragment for caches, e.g. "TEAM:3,7,9". */
    public String cacheKey() {
        if (isTenantWide()) {
            return level.name();
        }
        return level.name() + ":" + ownerIds.stream().sorted().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }
}
