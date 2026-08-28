package com.company.crm.campaign.controller;

import com.company.crm.campaign.dto.request.CampaignReqDto;
import com.company.crm.campaign.dto.request.CampaignStatusReqDto;
import com.company.crm.campaign.dto.response.CampaignResDto;
import com.company.crm.campaign.dto.response.CampaignSummaryDto;
import com.company.crm.campaign.service.CampaignService;
import com.company.crm.common.response.Response;
import com.company.crm.lead.dto.response.LeadResDto;
import com.company.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module 5 — Marketing Campaign Management.
 * Every endpoint is tenant-scoped in the service layer; super_admin (no tenant) has no access.
 * Roles mirror frontend/core/constants/permission.constant.js's CAMPAIGNS_* entries:
 * view = admin + marketing_executive + executive_owner, manage (create/edit/delete/status)
 * = admin + marketing_executive only.
 */
@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    private static final String VIEW_ROLES = "hasAnyRole('ADMIN', 'MARKETING_EXECUTIVE', 'EXECUTIVE_OWNER')";
    private static final String MANAGE_ROLES = "hasAnyRole('ADMIN', 'MARKETING_EXECUTIVE')";

    @GetMapping
    @PreAuthorize(VIEW_ROLES)
    public Response<List<CampaignResDto>> listCampaigns(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String search) {
        return Response.ok(campaignService.listCampaigns(currentUser, status, channel, search));
    }

    @GetMapping("/summary")
    @PreAuthorize(VIEW_ROLES)
    public Response<CampaignSummaryDto> getSummary(@AuthenticationPrincipal User currentUser) {
        return Response.ok(campaignService.getSummary(currentUser));
    }

    @GetMapping("/{campaignId}")
    @PreAuthorize(VIEW_ROLES)
    public Response<CampaignResDto> getCampaign(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long campaignId) {
        return Response.ok(campaignService.getCampaign(currentUser, campaignId));
    }

    @GetMapping("/{campaignId}/leads")
    @PreAuthorize(VIEW_ROLES)
    public Response<List<LeadResDto>> listCampaignLeads(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long campaignId) {
        return Response.ok(campaignService.listCampaignLeads(currentUser, campaignId));
    }

    @PostMapping
    @PreAuthorize(MANAGE_ROLES)
    public Response<CampaignResDto> createCampaign(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CampaignReqDto dto) {
        return Response.ok("Campaign created", campaignService.createCampaign(currentUser, dto));
    }

    @PutMapping("/{campaignId}")
    @PreAuthorize(MANAGE_ROLES)
    public Response<CampaignResDto> updateCampaign(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long campaignId,
            @Valid @RequestBody CampaignReqDto dto) {
        return Response.ok("Campaign updated", campaignService.updateCampaign(currentUser, campaignId, dto));
    }

    @DeleteMapping("/{campaignId}")
    @PreAuthorize(MANAGE_ROLES)
    public Response<Void> deleteCampaign(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long campaignId) {
        campaignService.deleteCampaign(currentUser, campaignId);
        return Response.ok("Campaign deleted", null);
    }

    @PatchMapping("/{campaignId}/status")
    @PreAuthorize(MANAGE_ROLES)
    public Response<CampaignResDto> changeStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long campaignId,
            @Valid @RequestBody CampaignStatusReqDto dto) {
        return Response.ok("Campaign status updated", campaignService.changeStatus(currentUser, campaignId, dto));
    }
}
