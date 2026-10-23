package com.company.crm.support.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.support.dto.request.TicketAssignmentRuleReqDto;
import com.company.crm.support.dto.response.TicketAssignmentRuleResDto;
import com.company.crm.support.service.TicketAssignmentRuleService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-6.3b: the tenant admin configures how new service tickets are auto-assigned. */
@RestController
@RequestMapping("/api/ticket-assignment-rules")
@PreAuthorize(RoleExpressions.TENANT_ADMIN)
@RequiredArgsConstructor
public class TicketAssignmentRuleController {

    private final TicketAssignmentRuleService ruleService;

    @GetMapping
    @Operation(summary = "FR-6.3b: ticket auto-assignment rules in evaluation order")
    public Response<List<TicketAssignmentRuleResDto>> list(@AuthenticationPrincipal User currentUser) {
        return Response.ok(ruleService.listRules(currentUser));
    }

    @PostMapping
    @Operation(summary = "FR-6.3b: add a rule (named agent, or round-robin when no agent is given)")
    public Response<TicketAssignmentRuleResDto> create(@AuthenticationPrincipal User currentUser,
                                                       @Valid @RequestBody TicketAssignmentRuleReqDto dto) {
        return Response.ok("Assignment rule added", ruleService.createRule(currentUser, dto));
    }

    @PutMapping("/{ruleId}")
    @Operation(summary = "FR-6.3b: replace a rule")
    public Response<TicketAssignmentRuleResDto> update(@AuthenticationPrincipal User currentUser, @PathVariable Long ruleId,
                                                       @Valid @RequestBody TicketAssignmentRuleReqDto dto) {
        return Response.ok("Assignment rule updated", ruleService.updateRule(currentUser, ruleId, dto));
    }

    @DeleteMapping("/{ruleId}")
    @Operation(summary = "FR-6.3b: delete a rule")
    public Response<Void> delete(@AuthenticationPrincipal User currentUser, @PathVariable Long ruleId) {
        ruleService.deleteRule(currentUser, ruleId);
        return Response.ok("Assignment rule deleted", null);
    }
}
