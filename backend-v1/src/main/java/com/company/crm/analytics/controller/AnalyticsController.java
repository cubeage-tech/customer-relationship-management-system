package com.company.crm.analytics.controller;

import com.company.crm.analytics.dto.AnalyticsDtos.CampaignReport;
import com.company.crm.analytics.dto.AnalyticsDtos.CustomerReport;
import com.company.crm.analytics.dto.AnalyticsDtos.FilterOptions;
import com.company.crm.analytics.dto.AnalyticsDtos.Headline;
import com.company.crm.analytics.dto.AnalyticsDtos.Preferences;
import com.company.crm.analytics.dto.AnalyticsDtos.RevenueReport;
import com.company.crm.analytics.dto.AnalyticsDtos.SalesReport;
import com.company.crm.analytics.dto.AnalyticsDtos.ServiceReport;
import com.company.crm.analytics.dto.AnalyticsDtos.TeamPerformance;
import com.company.crm.analytics.export.ReportRunner;
import com.company.crm.analytics.export.ReportRunner.Filters;
import com.company.crm.analytics.export.ReportRunner.Format;
import com.company.crm.analytics.export.ReportRunner.Rendered;
import com.company.crm.analytics.service.AnalyticsService;
import com.company.crm.analytics.service.ReportScope;
import com.company.crm.analytics.service.ReportScopeResolver;
import com.company.crm.analytics.service.ReportType;
import com.company.crm.analytics.service.ServiceCampaignReportService;
import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Module 9 — analytics and reporting: the configurable dashboard headline (FR-9.1) and the Sales,
 * Customer, Revenue and Team Performance reports (FR-9.2–9.5). Every report takes the FR-9.6 filters
 * (from, to, industry, teamId, userId), which only ever narrow the caller's own data scope. Also the
 * Service (REP-SVC) and Campaign (REP-CMP) reports, and PDF/XLSX export of any report (FR-9.7).
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private static final String SALES_REPORTS = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'EXECUTIVE_OWNER')";
    private static final String CUSTOMER_REPORTS = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'EXECUTIVE_OWNER', 'MARKETING_EXECUTIVE')";
    private static final String REVENUE_REPORTS = "hasAnyRole('ADMIN', 'FINANCE_APPROVER', 'EXECUTIVE_OWNER')";
    private static final String SERVICE_REPORTS = "hasAnyRole('ADMIN', 'SERVICE_AGENT', 'EXECUTIVE_OWNER')";
    private static final String CAMPAIGN_REPORTS = "hasAnyRole('ADMIN', 'MARKETING_EXECUTIVE', 'EXECUTIVE_OWNER')";

    private final AnalyticsService analyticsService;
    private final ReportScopeResolver scopeResolver;
    private final ServiceCampaignReportService serviceCampaignReports;
    private final ReportRunner reportRunner;

    @GetMapping("/headline")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-9.1: leads, conversions, conversion rate, revenue and growth vs the previous period")
    public Response<Headline> headline(@AuthenticationPrincipal User currentUser,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) String industry,
                                       @RequestParam(required = false) Long teamId,
                                       @RequestParam(required = false) Long userId) {
        ReportScope scope = scopeResolver.resolve(currentUser, from, to, industry, teamId, userId);
        return Response.ok(analyticsService.headline(currentUser, scope));
    }

    @GetMapping("/sales")
    @PreAuthorize(SALES_REPORTS)
    @Operation(summary = "FR-9.2: opportunities by stage and by owner, with values")
    public Response<SalesReport> sales(@AuthenticationPrincipal User currentUser,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) String industry,
                                       @RequestParam(required = false) Long teamId,
                                       @RequestParam(required = false) Long userId) {
        ReportScope scope = scopeResolver.resolve(currentUser, from, to, industry, teamId, userId);
        return Response.ok(analyticsService.sales(currentUser, scope));
    }

    @GetMapping("/customers")
    @PreAuthorize(CUSTOMER_REPORTS)
    @Operation(summary = "FR-9.3: customer counts, industry segments and communication activity")
    public Response<CustomerReport> customers(@AuthenticationPrincipal User currentUser,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(required = false) String industry,
                                              @RequestParam(required = false) Long teamId,
                                              @RequestParam(required = false) Long userId) {
        ReportScope scope = scopeResolver.resolve(currentUser, from, to, industry, teamId, userId);
        return Response.ok(analyticsService.customers(currentUser, scope));
    }

    @GetMapping("/revenue")
    @PreAuthorize(REVENUE_REPORTS)
    @Operation(summary = "FR-9.4: won revenue by month, product, industry and salesperson")
    public Response<RevenueReport> revenue(@AuthenticationPrincipal User currentUser,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) String industry,
                                           @RequestParam(required = false) Long teamId,
                                           @RequestParam(required = false) Long userId) {
        ReportScope scope = scopeResolver.resolve(currentUser, from, to, industry, teamId, userId);
        return Response.ok(analyticsService.revenue(currentUser, scope));
    }

    @GetMapping("/team-performance")
    @PreAuthorize(SALES_REPORTS)
    @Operation(summary = "FR-9.5: per salesperson and team — revenue, target attainment, activities and commission")
    public Response<TeamPerformance> teamPerformance(@AuthenticationPrincipal User currentUser,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     @RequestParam(required = false) String industry,
                                                     @RequestParam(required = false) Long teamId,
                                                     @RequestParam(required = false) Long userId) {
        ReportScope scope = scopeResolver.resolve(currentUser, from, to, industry, teamId, userId);
        return Response.ok(analyticsService.teamPerformance(currentUser, scope));
    }

    @GetMapping("/service")
    @PreAuthorize(SERVICE_REPORTS)
    @Operation(summary = "REP-SVC: ticket volume, resolution time, SLA compliance and feedback score; agents see their own tickets")
    public Response<ServiceReport> service(@AuthenticationPrincipal User currentUser,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) String priority,
                                           @RequestParam(required = false) Long technicianId) {
        ReportScope scope = scopeResolver.resolve(currentUser, from, to, null, null, null);
        return Response.ok(serviceCampaignReports.service(currentUser, scope, priority, technicianId));
    }

    @GetMapping("/campaigns")
    @PreAuthorize(CAMPAIGN_REPORTS)
    @Operation(summary = "REP-CMP: sent, opened, clicked and converted per campaign running in the period")
    public Response<CampaignReport> campaigns(@AuthenticationPrincipal User currentUser,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportScope scope = scopeResolver.resolve(currentUser, from, to, null, null, null);
        return Response.ok(serviceCampaignReports.campaigns(currentUser, scope));
    }

    /** The role rules per report live in {@link ReportType}; the runner applies them and the data scope. */
    @GetMapping("/export/{report}")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-9.7: download a report as PDF or Excel (format=pdf|xlsx), with the same filters and scope as on screen")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal User currentUser, @PathVariable String report,
                                         @RequestParam(defaultValue = "pdf") String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) String industry,
                                         @RequestParam(required = false) Long teamId,
                                         @RequestParam(required = false) Long userId,
                                         @RequestParam(required = false) String priority,
                                         @RequestParam(required = false) Long technicianId) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        Rendered file = reportRunner.render(currentUser, ReportType.fromCode(report),
                new Filters(start, end, industry, teamId, userId, priority, technicianId), Format.fromCode(format));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.bytes());
    }

    @GetMapping("/filter-options")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-9.6: the teams, salespeople and industries the caller may filter by")
    public Response<FilterOptions> filterOptions(@AuthenticationPrincipal User currentUser) {
        return Response.ok(analyticsService.filterOptions(currentUser));
    }

    @GetMapping("/dashboard-preferences")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-9.1: the caller's dashboard tiles and default period")
    public Response<Preferences> preferences(@AuthenticationPrincipal User currentUser) {
        return Response.ok(analyticsService.preferences(currentUser));
    }

    @PutMapping("/dashboard-preferences")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-9.1: choose which dashboard tiles to show, in order, and the default period")
    public Response<Preferences> savePreferences(@AuthenticationPrincipal User currentUser, @Valid @RequestBody Preferences dto) {
        return Response.ok("Dashboard saved", analyticsService.savePreferences(currentUser, dto));
    }
}
