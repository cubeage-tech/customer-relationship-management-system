package com.company.crm.common.scope;

import jakarta.persistence.TypedQuery;

/**
 * Applies a {@link DataScope} to a JPQL aggregate query: tenant-wide adds nothing, TEAM/OWN add
 * "and alias.owner.id in :scopeOwners". Keeps every summary query on one code path instead of
 * a tenant-wide and an owner-filtered copy of each.
 */
public final class ScopedJpql {

    private ScopedJpql() {
    }

    /** JPQL fragment to append after the tenant predicate. */
    public static String ownerClause(DataScope scope, String alias) {
        return scope.isTenantWide() ? "" : " and " + alias + ".owner.id in :scopeOwners";
    }

    public static <T> TypedQuery<T> bindOwners(TypedQuery<T> query, DataScope scope) {
        if (!scope.isTenantWide()) {
            query.setParameter("scopeOwners", scope.ownerIds());
        }
        return query;
    }
}
