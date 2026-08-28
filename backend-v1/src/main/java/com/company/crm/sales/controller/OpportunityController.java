package com.company.crm.sales.controller;

import com.company.crm.common.response.Response;
import com.company.crm.sales.dto.request.OpportunityReqDto;
import com.company.crm.sales.dto.request.OpportunityStageReqDto;
import com.company.crm.sales.dto.response.OpportunityResDto;
import com.company.crm.sales.dto.response.OpportunityStageSummaryDto;
import com.company.crm.sales.service.OpportunityService;
import com.company.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module 3 — Sales Pipeline Management.
 * Every endpoint is tenant-scoped in the service layer; super_admin (no tenant) has no access.
 * Roles mirror frontend/core/constants/permission.constant.js's OPPORTUNITIES_* entries:
 * view = admin + sales_manager + sales_executive + finance_approver + executive_owner,
 * create/edit/delete = admin + sales_manager + sales_executive (own only).
 */
@RestController
@RequestMapping("/api/opportunities")
@RequiredArgsConstructor
public class OpportunityController {

    private final OpportunityService opportunityService;

    private static final String VIEW_ROLES =
            "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'FINANCE_APPROVER', 'EXECUTIVE_OWNER')";
    private static final String EDIT_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')";

    @GetMapping
    @PreAuthorize(VIEW_ROLES)
    public Response<List<OpportunityResDto>> listOpportunities(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String search) {
        return Response.ok(opportunityService.listOpportunities(currentUser, stage, customerId, search));
    }

    @GetMapping("/summary")
    @PreAuthorize(VIEW_ROLES)
    public Response<List<OpportunityStageSummaryDto>> getStageSummary(@AuthenticationPrincipal User currentUser) {
        return Response.ok(opportunityService.getStageSummary(currentUser));
    }

    @GetMapping("/{opportunityId}")
    @PreAuthorize(VIEW_ROLES)
    public Response<OpportunityResDto> getOpportunity(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long opportunityId) {
        return Response.ok(opportunityService.getOpportunity(currentUser, opportunityId));
    }

    @PostMapping
    @PreAuthorize(EDIT_ROLES)
    public Response<OpportunityResDto> createOpportunity(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody OpportunityReqDto dto) {
        return Response.ok("Opportunity created", opportunityService.createOpportunity(currentUser, dto));
    }

    @PutMapping("/{opportunityId}")
    @PreAuthorize(EDIT_ROLES)
    public Response<OpportunityResDto> updateOpportunity(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long opportunityId,
            @Valid @RequestBody OpportunityReqDto dto) {
        return Response.ok("Opportunity updated", opportunityService.updateOpportunity(currentUser, opportunityId, dto));
    }

    @DeleteMapping("/{opportunityId}")
    @PreAuthorize(EDIT_ROLES)
    public Response<Void> deleteOpportunity(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long opportunityId) {
        opportunityService.deleteOpportunity(currentUser, opportunityId);
        return Response.ok("Opportunity deleted", null);
    }

    @PatchMapping("/{opportunityId}/stage")
    @PreAuthorize(EDIT_ROLES)
    public Response<OpportunityResDto> changeStage(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long opportunityId,
            @Valid @RequestBody OpportunityStageReqDto dto) {
        return Response.ok("Opportunity stage updated", opportunityService.changeStage(currentUser, opportunityId, dto));
    }
}
