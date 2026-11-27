package com.company.crm.common.security;

/**
 * Shared {@code @PreAuthorize} expressions, so role lists aren't re-typed per controller.
 * Role names are {@link com.company.crm.common.enums.RoleType} constant names.
 */
public final class RoleExpressions {

    public static final String SUPER_ADMIN = "hasRole('SUPER_ADMIN')";

    public static final String TENANT_ADMIN = "hasRole('ADMIN')";

    /** Every role that belongs to a tenant — i.e. everyone except platform staff. */
    public static final String ANY_TENANT_ROLE = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', "
            + "'MARKETING_EXECUTIVE', 'SERVICE_AGENT', 'FINANCE_APPROVER', 'EXECUTIVE_OWNER')";

    /** Any signed-in user, platform or tenant; services apply their own tenant scoping. */
    public static final String ANY_AUTHENTICATED = "isAuthenticated()";

    private RoleExpressions() {
    }
}
