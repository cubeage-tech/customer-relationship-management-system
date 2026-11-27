package com.company.crm.dashboard.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.crm.common.security.RoleExpressions;
import com.company.crm.dashboard.dto.response.SuperAdminTenantResDto;
import com.company.crm.dashboard.service.SuperAdminTenantService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/super-admin/tenants")
@RequiredArgsConstructor
public class SuperAdminTenantController {
    
    private final SuperAdminTenantService superAdminTenantService;

    // Cross-tenant data: platform staff only. Without this any signed-in tenant user could list every tenant.
    @GetMapping
    @PreAuthorize(RoleExpressions.SUPER_ADMIN)
    public List<SuperAdminTenantResDto> getAllTenants() {
        return superAdminTenantService.getAllTenants();
    }
}
