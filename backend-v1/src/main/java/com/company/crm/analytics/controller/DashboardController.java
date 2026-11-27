package com.company.crm.analytics.controller;

import com.company.crm.analytics.dto.AnalyticsDtos.Headline;
import com.company.crm.analytics.service.AnalyticsService;
import com.company.crm.analytics.service.ReportScopeResolver;
import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * API-G10 (SRS §10.2): GET /api/dashboard — the signed-in user's dashboard summary (FR-9.1: leads,
 * conversions, revenue and growth against the previous period), within their data scope. Role
 * dashboards keep their own endpoints under /api/dashboard/...
 */
@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final AnalyticsService analyticsService;
    private final ReportScopeResolver scopeResolver;

    @GetMapping("/api/dashboard")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "API-G10: dashboard summary — totals for the period and growth vs the previous one")
    public Response<Headline> dashboard(@AuthenticationPrincipal User currentUser,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) String industry,
                                        @RequestParam(required = false) Long teamId,
                                        @RequestParam(required = false) Long userId) {
        return Response.ok(analyticsService.headline(currentUser, scopeResolver.resolve(currentUser, from, to, industry, teamId, userId)));
    }
}
