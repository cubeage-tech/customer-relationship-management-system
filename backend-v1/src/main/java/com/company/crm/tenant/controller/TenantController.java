package com.company.crm.tenant.controller;

import com.company.crm.common.response.Response;
import com.company.crm.tenant.dto.response.TenantResDto;
import com.company.crm.tenant.service.TenantService;
import com.company.crm.user.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Response<List<TenantResDto>> listTenants() {
        return Response.ok(tenantService.listTenants());
    }

    @PatchMapping("/{tenantId}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Response<TenantResDto> deactivateTenant(@AuthenticationPrincipal User currentUser, @PathVariable Long tenantId) {
        return Response.ok("Tenant temporarily deactivated", tenantService.deactivateTenant(currentUser, tenantId));
    }

    @PatchMapping("/{tenantId}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Response<TenantResDto> restoreTenant(@AuthenticationPrincipal User currentUser, @PathVariable Long tenantId) {
        return Response.ok("Tenant restored", tenantService.restoreTenant(currentUser, tenantId));
    }
}
