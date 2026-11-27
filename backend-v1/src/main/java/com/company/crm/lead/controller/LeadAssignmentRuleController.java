package com.company.crm.lead.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.lead.dto.request.LeadAssignmentRuleReqDto;
import com.company.crm.lead.dto.response.LeadAssignmentRuleResDto;
import com.company.crm.lead.service.LeadAssignmentRuleService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-2.3: the tenant admin configures how new leads are auto-assigned. */
@RestController
@RequestMapping("/api/lead-assignment-rules")
@RequiredArgsConstructor
public class LeadAssignmentRuleController {

    private final LeadAssignmentRuleService ruleService;

    @GetMapping
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-2.3: auto-assignment rules in evaluation order")
    public Response<List<LeadAssignmentRuleResDto>> list(@AuthenticationPrincipal User currentUser) {
        return Response.ok(ruleService.listRules(currentUser));
    }

    @PostMapping
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-2.3: add a rule (assign to a user, or round-robin within a team)")
    public Response<LeadAssignmentRuleResDto> create(@AuthenticationPrincipal User currentUser,
                                                     @Valid @RequestBody LeadAssignmentRuleReqDto dto) {
        return Response.ok("Assignment rule added", ruleService.createRule(currentUser, dto));
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-2.3: replace a rule")
    public Response<LeadAssignmentRuleResDto> update(@AuthenticationPrincipal User currentUser,
                                                     @PathVariable Long ruleId,
                                                     @Valid @RequestBody LeadAssignmentRuleReqDto dto) {
        return Response.ok("Assignment rule updated", ruleService.updateRule(currentUser, ruleId, dto));
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-2.3: delete a rule")
    public Response<Void> delete(@AuthenticationPrincipal User currentUser, @PathVariable Long ruleId) {
        ruleService.deleteRule(currentUser, ruleId);
        return Response.ok("Assignment rule deleted", null);
    }
}
