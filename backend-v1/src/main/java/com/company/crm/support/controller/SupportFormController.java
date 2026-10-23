package com.company.crm.support.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.support.service.SupportFormService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** FR-6.1: the tenant admin's shareable "Report an issue" link for customers. */
@RestController
@RequestMapping("/api/support-form")
@PreAuthorize(RoleExpressions.TENANT_ADMIN)
@RequiredArgsConstructor
public class SupportFormController {

    private final SupportFormService supportFormService;

    @GetMapping
    @Operation(summary = "FR-6.1: the customer support form link (created on first request)")
    public Response<Map<String, String>> link(@AuthenticationPrincipal User currentUser) {
        return Response.ok(Map.of("url", supportFormService.link(currentUser)));
    }

    @PostMapping("/reset")
    @Operation(summary = "FR-6.1: replace the link; the old one stops working")
    public Response<Map<String, String>> reset(@AuthenticationPrincipal User currentUser) {
        return Response.ok("Support link replaced", Map.of("url", supportFormService.resetLink(currentUser)));
    }
}
