package com.company.crm.rolepermission;

import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;

/**
 * ROLE-8: refuses an API call when the tenant switched off, for the caller's role, every
 * permission the call needs. The role checks on the endpoints stay the ceiling; this only ever
 * takes access away. A call that matches no rule, or a role holding none of the rule's
 * permissions by default, is left to those checks.
 */
@Component
@RequiredArgsConstructor
public class PermissionGuard implements HandlerInterceptor {

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    /** methods ("*" = any), path pattern, and the permissions that each allow the call. First match wins. */
    record Rule(String methods, String pattern, Set<String> permissions) {
        boolean matches(String method, String path) {
            return ("*".equals(methods) || Set.of(methods.split(",")).contains(method)) && PATHS.match(pattern, path);
        }
    }

    private static Rule r(String methods, String pattern, String... permissions) {
        return new Rule(methods, pattern, Set.of(permissions));
    }

    static final List<Rule> RULES = List.of(
            // Customers
            r("PATCH", "/api/customers/*/archive", "customers.delete"),
            r("PATCH", "/api/customers/*/restore", "customers.delete"),
            r("POST", "/api/customers", "customers.create"),
            r("GET", "/api/customers/**", "customers.view"),
            r("GET", "/api/customers", "customers.view"),
            r("POST,PUT,PATCH,DELETE", "/api/customers/**", "customers.edit"),
            // Leads
            r("PATCH", "/api/leads/*/assign", "leads.assign"),
            r("PATCH", "/api/leads/*/team", "leads.assign"),
            r("POST", "/api/leads", "leads.create"),
            r("DELETE", "/api/leads/*", "leads.delete"),
            r("GET", "/api/leads/**", "leads.view"),
            r("GET", "/api/leads", "leads.view"),
            r("PUT,PATCH", "/api/leads/**", "leads.edit"),
            // Opportunities
            r("POST", "/api/opportunities", "opportunities.create"),
            r("DELETE", "/api/opportunities/*", "opportunities.delete"),
            r("GET", "/api/opportunities/**", "opportunities.view"),
            r("GET", "/api/opportunities", "opportunities.view"),
            r("PUT,PATCH", "/api/opportunities/**", "opportunities.edit"),
            // Quotations
            r("PATCH", "/api/quotations/*/discount/*", "quotations.approve", "quotations.approveDiscount"),
            r("POST", "/api/quotations", "quotations.create"),
            r("GET", "/api/quotations/**", "quotations.view"),
            r("GET", "/api/quotations", "quotations.view"),
            r("PUT,PATCH", "/api/quotations/**", "quotations.edit"),
            // Service tickets
            r("PATCH", "/api/tickets/*/status", "serviceTickets.edit", "serviceTickets.resolve"),
            r("POST", "/api/tickets", "serviceTickets.create"),
            r("GET", "/api/tickets/**", "serviceTickets.view"),
            r("GET", "/api/tickets", "serviceTickets.view"),
            r("PUT,PATCH,POST", "/api/tickets/**", "serviceTickets.edit"),
            // Campaigns, segments, promotions
            r("GET", "/api/campaigns/**", "campaigns.view"),
            r("GET", "/api/campaigns", "campaigns.view"),
            r("POST,PUT,PATCH,DELETE", "/api/campaigns/**", "campaigns.manage"),
            r("POST", "/api/campaigns", "campaigns.manage"),
            r("GET", "/api/segments/**", "campaigns.view"),
            r("GET", "/api/segments", "campaigns.view"),
            r("POST,PUT,PATCH,DELETE", "/api/segments/**", "campaigns.manage"),
            r("POST", "/api/segments", "campaigns.manage"),
            r("POST", "/api/promotions", "campaigns.manage"),
            r("PUT", "/api/promotions/*", "campaigns.manage"),
            // Reports (both /api/analytics and /api/reports), on screen and exported
            r("GET", "/api/*/sales", "reports.sales"),
            r("GET", "/api/*/team-performance", "reports.sales"),
            r("GET", "/api/*/customers", "reports.sales", "reports.campaign"),
            r("GET", "/api/*/lead-sources", "reports.sales", "reports.campaign"),
            r("GET", "/api/*/revenue", "reports.revenue"),
            r("GET", "/api/*/service", "reports.service"),
            r("GET", "/api/*/campaigns", "reports.campaign"),
            r("GET", "/api/*/export/sales", "reports.sales"),
            r("GET", "/api/*/export/team-performance", "reports.sales"),
            r("GET", "/api/*/export/customers", "reports.sales", "reports.campaign"),
            r("GET", "/api/*/export/lead-sources", "reports.sales", "reports.campaign"),
            r("GET", "/api/*/export/revenue", "reports.revenue"),
            r("GET", "/api/*/export/service", "reports.service"),
            r("GET", "/api/*/export/campaigns", "reports.campaign"),
            // Communications and meetings
            r("POST", "/api/communications/*", "communications.send"),
            r("POST,PUT,PATCH", "/api/meetings/**", "communications.send"),
            r("POST", "/api/meetings", "communications.send"),
            // Administration
            r("GET", "/api/users/**", "users.view"),
            r("GET", "/api/users", "users.view"),
            r("POST,PUT,PATCH,DELETE", "/api/users/**", "users.manage"),
            r("POST", "/api/users", "users.manage"),
            r("GET", "/api/audit-events/**", "audit.view"),
            r("GET", "/api/audit-events", "audit.view"));

    private final RolePermissionService rolePermissions;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user) || user.getTenant() == null || user.getRole() == null) {
            return true;
        }
        RoleType role = user.getRole().getName();
        if (!PermissionCatalog.customizable(role)) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Rule rule = RULES.stream().filter(x -> x.matches(request.getMethod(), path)).findFirst().orElse(null);
        if (rule == null) {
            return true;
        }
        Set<String> held = PermissionCatalog.defaults(role);
        List<String> needed = rule.permissions().stream().filter(held::contains).toList();
        if (needed.isEmpty()) {
            return true;
        }
        Set<String> revoked = rolePermissions.revoked(user.getTenant().getId(), role);
        if (needed.stream().allMatch(revoked::contains)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your organisation has switched this off for your role", "PERMISSION_DISABLED");
        }
        return true;
    }
}
