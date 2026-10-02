package com.company.crm.sales_team.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.sales_team.dto.request.SalesTeamMembersReqDto;
import com.company.crm.sales_team.dto.request.SalesTeamReqDto;
import com.company.crm.sales_team.dto.responce.SalesTeamResDto;
import com.company.crm.sales_team.service.SalesTeamService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tenant admin manages sales teams. New API: no existing endpoint manages teams or membership
 * (UserController only lists and creates users).
 */
@RestController
@RequestMapping("/api/sales-teams")
@PreAuthorize(RoleExpressions.TENANT_ADMIN)
@RequiredArgsConstructor
@Tag(name = "Sales teams", description = "Teams that define what a sales manager can see (tenant admin only)")
public class SalesTeamController {

    private final SalesTeamService salesTeamService;

    @GetMapping
    @Operation(summary = "List the tenant's sales teams with their manager and members")
    public Response<List<SalesTeamResDto>> listTeams(@AuthenticationPrincipal User currentUser) {
        return Response.ok(salesTeamService.listTeams(currentUser));
    }

    @PostMapping
    @Operation(summary = "Create a team", description = "409 if the name is taken; manager must be a sales_manager of this tenant.")
    public Response<SalesTeamResDto> createTeam(@AuthenticationPrincipal User currentUser,
                                                @Valid @RequestBody SalesTeamReqDto dto) {
        return Response.ok("Team created", salesTeamService.createTeam(currentUser, dto));
    }

    @PutMapping("/{teamId}")
    @Operation(summary = "Rename a team or change its manager")
    public Response<SalesTeamResDto> updateTeam(@AuthenticationPrincipal User currentUser,
                                                @PathVariable Long teamId,
                                                @Valid @RequestBody SalesTeamReqDto dto) {
        return Response.ok("Team updated", salesTeamService.updateTeam(currentUser, teamId, dto));
    }

    @PutMapping("/{teamId}/members")
    @Operation(summary = "Replace the team's members",
            description = "Members must be sales executives of this tenant; they leave any previous team.")
    public Response<SalesTeamResDto> setMembers(@AuthenticationPrincipal User currentUser,
                                                @PathVariable Long teamId,
                                                @Valid @RequestBody SalesTeamMembersReqDto dto) {
        return Response.ok("Team members updated", salesTeamService.setMembers(currentUser, teamId, dto));
    }

    @DeleteMapping("/{teamId}")
    @Operation(summary = "Delete a team", description = "Members are un-assigned; their records are untouched.")
    public Response<Void> deleteTeam(@AuthenticationPrincipal User currentUser, @PathVariable Long teamId) {
        salesTeamService.deleteTeam(currentUser, teamId);
        return Response.ok("Team deleted", null);
    }
}
