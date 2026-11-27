package com.company.crm.salesforce.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.salesforce.dto.SalesForceDtos.ActivitySummary;
import com.company.crm.salesforce.dto.SalesForceDtos.AttendanceRes;
import com.company.crm.salesforce.dto.SalesForceDtos.CommissionEntryRes;
import com.company.crm.salesforce.dto.SalesForceDtos.CommissionRuleReq;
import com.company.crm.salesforce.dto.SalesForceDtos.CommissionRuleRes;
import com.company.crm.salesforce.dto.SalesForceDtos.LocationReq;
import com.company.crm.salesforce.dto.SalesForceDtos.MyAttendance;
import com.company.crm.salesforce.dto.SalesForceDtos.TargetReq;
import com.company.crm.salesforce.dto.SalesForceDtos.TargetRes;
import com.company.crm.salesforce.dto.SalesForceDtos.VisitReq;
import com.company.crm.salesforce.dto.SalesForceDtos.VisitRes;
import com.company.crm.salesforce.service.AttendanceService;
import com.company.crm.salesforce.service.CommissionService;
import com.company.crm.salesforce.service.SalesActivityService;
import com.company.crm.salesforce.service.SalesTargetService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Module 8 — sales force management: targets and attainment (FR-8.1/8.2), activities (FR-8.3),
 * attendance (FR-8.4) and commission (FR-8.5). What each caller sees follows SalesForceScope.
 * API-G09: the SRS §10.2 paths /targets, /attendance and /commissions; targets also answer at
 * /sales-targets, which the web app uses.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SalesForceController {

    private static final String SALES_VIEW = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'EXECUTIVE_OWNER')";
    private static final String SALES_DO = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')";
    private static final String MANAGERS = "hasAnyRole('ADMIN', 'SALES_MANAGER')";
    private static final String TEAM_VIEW = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'EXECUTIVE_OWNER')";
    private static final String COMMISSION_VIEW = "hasAnyRole('ADMIN', 'FINANCE_APPROVER', 'EXECUTIVE_OWNER', 'SALES_MANAGER', 'SALES_EXECUTIVE')";
    private static final String COMMISSION_SETTLE = "hasAnyRole('ADMIN', 'FINANCE_APPROVER')";

    private final SalesTargetService targetService;
    private final SalesActivityService activityService;
    private final AttendanceService attendanceService;
    private final CommissionService commissionService;

    // ==================== FR-8.1/8.2 targets ====================

    @GetMapping({"/sales-targets", "/targets"})
    @PreAuthorize(SALES_VIEW)
    @Operation(summary = "FR-8.2: targets with live attainment from won deals")
    public Response<List<TargetRes>> targets(@AuthenticationPrincipal User currentUser) {
        return Response.ok(targetService.list(currentUser));
    }

    @PostMapping({"/sales-targets", "/targets"})
    @PreAuthorize(MANAGERS)
    @Operation(summary = "FR-8.1: set a revenue/units target for a salesperson or team")
    public Response<TargetRes> createTarget(@AuthenticationPrincipal User currentUser, @Valid @RequestBody TargetReq dto) {
        return Response.ok("Target set", targetService.create(currentUser, dto));
    }

    @PutMapping({"/sales-targets/{targetId}", "/targets/{targetId}"})
    @PreAuthorize(MANAGERS)
    @Operation(summary = "FR-8.1: change a target")
    public Response<TargetRes> updateTarget(@AuthenticationPrincipal User currentUser, @PathVariable Long targetId,
                                            @Valid @RequestBody TargetReq dto) {
        return Response.ok("Target updated", targetService.update(currentUser, targetId, dto));
    }

    @DeleteMapping({"/sales-targets/{targetId}", "/targets/{targetId}"})
    @PreAuthorize(MANAGERS)
    @Operation(summary = "FR-8.1: delete a target")
    public Response<Void> deleteTarget(@AuthenticationPrincipal User currentUser, @PathVariable Long targetId) {
        targetService.delete(currentUser, targetId);
        return Response.ok("Target deleted", null);
    }

    // ==================== FR-8.3 activities ====================

    @PostMapping("/sales-activities/visits")
    @PreAuthorize(SALES_DO)
    @Operation(summary = "FR-8.3: log a field visit")
    public Response<VisitRes> logVisit(@AuthenticationPrincipal User currentUser, @Valid @RequestBody VisitReq dto) {
        return Response.ok("Visit logged", activityService.logVisit(currentUser, dto));
    }

    @GetMapping("/sales-activities/visits")
    @PreAuthorize(SALES_VIEW)
    @Operation(summary = "FR-8.3: logged visits in a period (optionally one salesperson)")
    public Response<List<VisitRes>> visits(@AuthenticationPrincipal User currentUser,
                                           @RequestParam(required = false) Long userId,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Response.ok(activityService.visits(currentUser, userId, from, to));
    }

    @GetMapping("/sales-activities/summary")
    @PreAuthorize(SALES_VIEW)
    @Operation(summary = "FR-8.3: calls, meetings, quotations sent and visits per salesperson in a period")
    public Response<List<ActivitySummary>> activitySummary(@AuthenticationPrincipal User currentUser,
                                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Response.ok(activityService.summary(currentUser, from, to));
    }

    // ==================== FR-8.4 attendance ====================

    @GetMapping("/attendance/me")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-8.4: am I checked in, and my recent attendance")
    public Response<MyAttendance> myAttendance(@AuthenticationPrincipal User currentUser) {
        return Response.ok(attendanceService.mine(currentUser));
    }

    @PostMapping("/attendance/check-in")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-8.4: check in (optionally with the device location)")
    public Response<AttendanceRes> checkIn(@AuthenticationPrincipal User currentUser, @Valid @RequestBody(required = false) LocationReq location) {
        return Response.ok("Checked in", attendanceService.checkIn(currentUser, location));
    }

    @PostMapping("/attendance/check-out")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-8.4: check out (optionally with the device location)")
    public Response<AttendanceRes> checkOut(@AuthenticationPrincipal User currentUser, @Valid @RequestBody(required = false) LocationReq location) {
        return Response.ok("Checked out", attendanceService.checkOut(currentUser, location));
    }

    @GetMapping("/attendance")
    @PreAuthorize(TEAM_VIEW)
    @Operation(summary = "FR-8.4: attendance of the people I manage in a period")
    public Response<List<AttendanceRes>> attendance(@AuthenticationPrincipal User currentUser,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Response.ok(attendanceService.report(currentUser, from, to));
    }

    // ==================== FR-8.5 / AC-8.2 commission ====================

    @GetMapping("/commissions")
    @PreAuthorize(COMMISSION_VIEW)
    @Operation(summary = "AC-8.2: commissions recorded on won deals (status: pending/approved/paid/void)")
    public Response<List<CommissionEntryRes>> commissions(@AuthenticationPrincipal User currentUser,
                                                          @RequestParam(required = false) String status) {
        return Response.ok(commissionService.entries(currentUser, status));
    }

    @PatchMapping("/commissions/{entryId}/approve")
    @PreAuthorize(COMMISSION_SETTLE)
    @Operation(summary = "FR-8.5: approve a pending commission")
    public Response<CommissionEntryRes> approve(@AuthenticationPrincipal User currentUser, @PathVariable Long entryId) {
        return Response.ok("Commission approved", commissionService.approve(currentUser, entryId));
    }

    @PatchMapping("/commissions/{entryId}/paid")
    @PreAuthorize(COMMISSION_SETTLE)
    @Operation(summary = "FR-8.5: mark an approved commission as paid")
    public Response<CommissionEntryRes> markPaid(@AuthenticationPrincipal User currentUser, @PathVariable Long entryId) {
        return Response.ok("Commission marked paid", commissionService.markPaid(currentUser, entryId));
    }

    @GetMapping("/commission-rules")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-8.5: commission rules in evaluation order")
    public Response<List<CommissionRuleRes>> commissionRules(@AuthenticationPrincipal User currentUser) {
        return Response.ok(commissionService.rules(currentUser));
    }

    @PostMapping("/commission-rules")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-8.5: add a rule (rate % of deal value; one salesperson, one team or everyone)")
    public Response<CommissionRuleRes> createCommissionRule(@AuthenticationPrincipal User currentUser, @Valid @RequestBody CommissionRuleReq dto) {
        return Response.ok("Commission rule added", commissionService.createRule(currentUser, dto));
    }

    @PutMapping("/commission-rules/{ruleId}")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-8.5: change a rule (recorded commissions keep their rate)")
    public Response<CommissionRuleRes> updateCommissionRule(@AuthenticationPrincipal User currentUser, @PathVariable Long ruleId,
                                                            @Valid @RequestBody CommissionRuleReq dto) {
        return Response.ok("Commission rule updated", commissionService.updateRule(currentUser, ruleId, dto));
    }

    @DeleteMapping("/commission-rules/{ruleId}")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-8.5: delete a rule")
    public Response<Void> deleteCommissionRule(@AuthenticationPrincipal User currentUser, @PathVariable Long ruleId) {
        commissionService.deleteRule(currentUser, ruleId);
        return Response.ok("Commission rule deleted", null);
    }
}
