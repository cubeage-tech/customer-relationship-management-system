package com.company.crm.rolepermission;

import com.company.crm.common.enums.RoleType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.company.crm.common.enums.RoleType.*;

/**
 * ROLE-8: the built-in tenant role matrix (SRS 14). Mirrors ROLE_PERMISSIONS in the web app's
 * permission.constant.js; keep the two in step. It is the ceiling for each role: a tenant can
 * switch these off per role, not add to them.
 */
public final class PermissionCatalog {

    private PermissionCatalog() {
    }

    /** Every tenant permission key, in display order. */
    public static final List<String> TENANT_PERMISSIONS = List.of(
            "customers.view", "customers.create", "customers.edit", "customers.delete",
            "leads.view", "leads.create", "leads.edit", "leads.delete", "leads.assign",
            "opportunities.view", "opportunities.create", "opportunities.edit", "opportunities.delete",
            "quotations.view", "quotations.create", "quotations.edit", "quotations.approve", "quotations.approveDiscount",
            "serviceTickets.view", "serviceTickets.create", "serviceTickets.edit", "serviceTickets.resolve",
            "campaigns.view", "campaigns.manage",
            "reports.view", "reports.sales", "reports.campaign", "reports.service", "reports.revenue",
            "users.view", "users.manage", "settings.view", "settings.manage", "audit.view", "billing.view", "teams.manage",
            "communications.send");

    private static Set<String> of(String... keys) {
        return new LinkedHashSet<>(List.of(keys));
    }

    private static final Map<RoleType, Set<String>> DEFAULTS = Map.of(
            ADMIN, new LinkedHashSet<>(TENANT_PERMISSIONS),
            SALES_MANAGER, of("customers.view", "communications.send", "customers.create", "customers.edit", "customers.delete",
                    "leads.view", "leads.create", "leads.edit", "leads.delete", "leads.assign",
                    "opportunities.view", "opportunities.create", "opportunities.edit", "opportunities.delete",
                    "quotations.view", "quotations.create", "quotations.edit", "quotations.approve",
                    "serviceTickets.view", "reports.view", "reports.sales", "users.view", "settings.view"),
            SALES_EXECUTIVE, of("customers.view", "communications.send", "customers.edit",
                    "leads.view", "leads.create", "leads.edit", "leads.delete",
                    "opportunities.view", "opportunities.create", "opportunities.edit", "opportunities.delete",
                    "quotations.view", "quotations.create", "quotations.edit", "serviceTickets.create", "reports.view"),
            MARKETING_EXECUTIVE, of("customers.view", "leads.view", "campaigns.view", "campaigns.manage", "reports.view", "reports.campaign"),
            SERVICE_AGENT, of("customers.view", "communications.send", "serviceTickets.view", "serviceTickets.create",
                    "serviceTickets.edit", "serviceTickets.resolve", "reports.view", "reports.service"),
            FINANCE_APPROVER, of("customers.view", "leads.view", "opportunities.view", "quotations.view", "quotations.approveDiscount",
                    "reports.view", "reports.revenue"),
            EXECUTIVE_OWNER, of("audit.view", "customers.view", "leads.view", "opportunities.view", "quotations.view",
                    "serviceTickets.view", "campaigns.view", "reports.view", "reports.sales", "reports.campaign",
                    "reports.service", "reports.revenue"));

    /** The role's default permissions (empty for the platform role). */
    public static Set<String> defaults(RoleType role) {
        return DEFAULTS.getOrDefault(role, Set.of());
    }

    /** Roles a tenant admin may restrict: every tenant role except the admin itself (so it can't lock itself out). */
    public static boolean customizable(RoleType role) {
        return role != null && role != ADMIN && role != SUPER_ADMIN && DEFAULTS.containsKey(role);
    }
}
