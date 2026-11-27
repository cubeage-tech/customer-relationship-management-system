package com.company.crm.dashboard.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.dashboard.dto.response.TenantAdminDashboardResDto;
import com.company.crm.dashboard.service.TenantAdminDashboardService;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class TenantAdminDashboardController {

    private final TenantAdminDashboardService tenantAdminDashboardService;
    private final UserRepository userRepository;

    @GetMapping("/tenant-admin-status")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    public ResponseEntity<Response<TenantAdminDashboardResDto>> getTenantAdminDashboardStatus(
            org.springframework.security.core.Authentication authenticator) {

        String email = authenticator.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getTenant() == null) {
            throw new RuntimeException("User is not associated with any tenant");
        }

        Long tenantId = user.getTenant().getId();

        TenantAdminDashboardResDto dashboardResDto =
                tenantAdminDashboardService.getTenantAdminDashboardResDto(tenantId);

        return ResponseEntity.ok(Response.ok(dashboardResDto));
    }
}