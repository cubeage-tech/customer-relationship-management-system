package com.company.crm.lead.controller;

import com.company.crm.common.pagination.PageRequestFactory;
import io.swagger.v3.oas.annotations.Operation;
import com.company.crm.lead.dto.response.LeadSummaryDto;
import com.company.crm.common.response.Response;
import com.company.crm.lead.dto.request.LeadAssignReqDto;
import com.company.crm.lead.dto.request.LeadReqDto;
import com.company.crm.lead.dto.request.LeadStageReqDto;
import com.company.crm.lead.dto.request.LeadTeamReqDto;
import com.company.crm.lead.dto.response.LeadResDto;
import com.company.crm.lead.dto.response.LeadTeamOptionDto;
import com.company.crm.lead.service.LeadService;
import com.company.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module 2 — Lead Management.
 * Every endpoint is tenant-scoped in the service layer; super_admin (no tenant) has no access.
 * Roles mirror frontend/core/constants/permission.constant.js's LEADS_* entries:
 * view = every tenant role except service_agent, create/edit/delete = admin + sales_manager +
 * sales_executive (own only), reassign (LEADS_ASSIGN) = admin + sales_manager only.
 */
@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'MARKETING_EXECUTIVE', 'FINANCE_APPROVER', 'EXECUTIVE_OWNER')")
    @Operation(summary = "Lead counts for dashboards (total, open, new this month) within your data scope")
    public Response<LeadSummaryDto> getSummary(@AuthenticationPrincipal User currentUser) {
        return Response.ok(leadService.getSummary(currentUser));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'MARKETING_EXECUTIVE', 'FINANCE_APPROVER', 'EXECUTIVE_OWNER')")
    // NFR-7.1b: without page/size the original array; with them a PageResponse (sort=createdAt|leadName|companyName|followUpDate|stage[,asc|desc]).
    public Response<?> listLeads(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        if (PageRequestFactory.isPaged(page, size)) {
            return Response.ok(leadService.listLeadsPage(currentUser, stage, source, industry, search, ownerId, page, size, sort));
        }
        return Response.ok(leadService.listLeads(currentUser, stage, source, industry, search, ownerId));
    }

    @GetMapping("/{leadId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'MARKETING_EXECUTIVE', 'FINANCE_APPROVER', 'EXECUTIVE_OWNER')")
    public Response<LeadResDto> getLead(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long leadId) {
        return Response.ok(leadService.getLead(currentUser, leadId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<LeadResDto> createLead(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody LeadReqDto dto) {
        return Response.ok("Lead created", leadService.createLead(currentUser, dto));
    }

    @PutMapping("/{leadId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<LeadResDto> updateLead(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long leadId,
            @Valid @RequestBody LeadReqDto dto) {
        return Response.ok("Lead updated", leadService.updateLead(currentUser, leadId, dto));
    }

    @DeleteMapping("/{leadId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<Void> deleteLead(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long leadId) {
        leadService.deleteLead(currentUser, leadId);
        return Response.ok("Lead deleted", null);
    }

    @PatchMapping("/{leadId}/stage")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<LeadResDto> changeStage(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long leadId,
            @Valid @RequestBody LeadStageReqDto dto) {
        return Response.ok("Lead stage updated", leadService.changeStage(currentUser, leadId, dto));
    }

    @PatchMapping("/{leadId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public Response<LeadResDto> assignLead(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long leadId,
            @Valid @RequestBody LeadAssignReqDto dto) {
        return Response.ok("Lead reassigned", leadService.assignLead(currentUser, leadId, dto.getOwnerId()));
    }

    @PatchMapping("/{leadId}/team")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    @Operation(summary = "FR-2.3: assign the lead to a sales team (teamId null clears it); the owner is kept")
    public Response<LeadResDto> assignTeam(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long leadId,
            @RequestBody LeadTeamReqDto dto) {
        return Response.ok("Lead team updated", leadService.assignTeam(currentUser, leadId, dto.getTeamId()));
    }

    @GetMapping("/assignable-teams")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    @Operation(summary = "FR-2.3: sales teams a lead can be assigned to")
    public Response<List<LeadTeamOptionDto>> listAssignableTeams(@AuthenticationPrincipal User currentUser) {
        return Response.ok(leadService.listAssignableTeams(currentUser));
    }
}
