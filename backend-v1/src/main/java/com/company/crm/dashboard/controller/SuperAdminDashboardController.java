package com.company.crm.dashboard.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.crm.common.response.Response;
import com.company.crm.dashboard.dto.response.SuperAdminDashboardResDto;
import com.company.crm.dashboard.service.SuperAdminDashboardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class SuperAdminDashboardController {

    private final SuperAdminDashboardService dashboardService;

    @GetMapping("/super-admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Response<SuperAdminDashboardResDto> getSuperAdminDashboard() {
        return Response.ok(dashboardService.getDashboard());
    }
}