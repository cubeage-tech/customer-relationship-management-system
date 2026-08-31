package com.company.crm.lead.controller;

import com.company.crm.common.response.Response;
import com.company.crm.lead.dto.request.LeadAssignReqDto;
import com.company.crm.lead.dto.request.LeadReqDto;
import com.company.crm.lead.dto.request.LeadStageReqDto;
import com.company.crm.lead.dto.response.LeadResDto;
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

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'MARKETING_EXECUTIVE', 'FINANCE_APPROVER', 'EXECUTIVE_OWNER')")
    public Response<List<LeadResDto>> listLeads(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String search) {
        return Response.ok(leadService.listLeads(currentUser, stage, source, industry, search));
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
}
