package com.company.crm.analytics.controller;

import com.company.crm.analytics.dto.AnalyticsDtos.ScheduleReq;
import com.company.crm.analytics.dto.AnalyticsDtos.ScheduleRes;
import com.company.crm.analytics.service.ReportScheduleService;
import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REP-SCH: reports generated on a schedule and emailed. Which report a user may schedule follows
 * the same role rules as viewing it (checked in the service); each schedule runs as its owner.
 */
@RestController
@RequestMapping("/api/report-schedules")
@RequiredArgsConstructor
public class ReportScheduleController {

    private final ReportScheduleService scheduleService;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "REP-SCH: your report schedules (admins: everyone's in the company)")
    public Response<List<ScheduleRes>> list(@AuthenticationPrincipal User currentUser) {
        return Response.ok(scheduleService.list(currentUser));
    }

    @PostMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "REP-SCH: schedule a report (daily, weekly or monthly; PDF or Excel) to be emailed")
    public Response<ScheduleRes> create(@AuthenticationPrincipal User currentUser, @Valid @RequestBody ScheduleReq dto) {
        return Response.ok("Report scheduled", scheduleService.create(currentUser, dto));
    }

    @PutMapping("/{scheduleId}")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "REP-SCH: change a schedule (its owner or an admin)")
    public Response<ScheduleRes> update(@AuthenticationPrincipal User currentUser, @PathVariable Long scheduleId,
                                        @Valid @RequestBody ScheduleReq dto) {
        return Response.ok("Schedule updated", scheduleService.update(currentUser, scheduleId, dto));
    }

    @DeleteMapping("/{scheduleId}")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "REP-SCH: delete a schedule (its owner or an admin)")
    public Response<Void> delete(@AuthenticationPrincipal User currentUser, @PathVariable Long scheduleId) {
        scheduleService.delete(currentUser, scheduleId);
        return Response.ok("Schedule deleted", null);
    }

    @PostMapping("/{scheduleId}/send")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "REP-SCH: generate and email the report now, without changing the schedule")
    public Response<ScheduleRes> sendNow(@AuthenticationPrincipal User currentUser, @PathVariable Long scheduleId) {
        return Response.ok("Report sent", scheduleService.sendNow(currentUser, scheduleId));
    }
}
