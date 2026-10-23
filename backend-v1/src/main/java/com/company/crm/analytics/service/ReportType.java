package com.company.crm.analytics.service;

import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import lombok.Getter;

import java.util.Arrays;
import java.util.Set;

import static com.company.crm.common.enums.RoleType.*;

/**
 * REP-SCOPE: every report and the roles allowed to run it — the same rules for viewing, exporting
 * (FR-9.7) and scheduling (REP-SCH). These mirror the @PreAuthorize rules on AnalyticsController.
 */
@Getter
public enum ReportType {
    SALES("sales", "Sales report", Set.of(ADMIN, SALES_MANAGER, EXECUTIVE_OWNER)),
    CUSTOMERS("customers", "Customer report", Set.of(ADMIN, SALES_MANAGER, EXECUTIVE_OWNER, MARKETING_EXECUTIVE)),
    REVENUE("revenue", "Revenue report", Set.of(ADMIN, FINANCE_APPROVER, EXECUTIVE_OWNER)),
    TEAM("team-performance", "Team performance report", Set.of(ADMIN, SALES_MANAGER, EXECUTIVE_OWNER)),
    SERVICE("service", "Service report", Set.of(ADMIN, SERVICE_AGENT, EXECUTIVE_OWNER)),
    CAMPAIGNS("campaigns", "Campaign performance report", Set.of(ADMIN, MARKETING_EXECUTIVE, EXECUTIVE_OWNER));

    private final String code;
    private final String title;
    private final Set<RoleType> roles;

    ReportType(String code, String title, Set<RoleType> roles) {
        this.code = code;
        this.title = title;
        this.roles = roles;
    }

    public static ReportType fromCode(String code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown report: " + code));
    }

    public boolean allows(RoleType role) {
        return roles.contains(role);
    }
}
