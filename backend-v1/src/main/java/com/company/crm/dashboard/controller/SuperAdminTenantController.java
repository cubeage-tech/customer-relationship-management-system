package com.company.crm.dashboard.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.crm.dashboard.dto.response.SuperAdminTenantResDto;
import com.company.crm.dashboard.service.SuperAdminTenantService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/super-admin/tenants")
@RequiredArgsConstructor
public class SuperAdminTenantController {
    
    private final SuperAdminTenantService superAdminTenantService;

    @GetMapping
    public List<SuperAdminTenantResDto> getAllTenants() {
        return superAdminTenantService.getAllTenants();
    }
}
