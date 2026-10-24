package com.company.crm.tenant.controller;

import com.company.crm.common.response.Response;
import com.company.crm.tenant.dto.TenantSettingsDtos.SettingsReq;
import com.company.crm.tenant.dto.TenantSettingsDtos.SettingsRes;
import com.company.crm.tenant.service.TenantSettingsService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** NFR-7.6b: the tenant's SLA windows and discount-approval threshold. */
@RestController
@RequestMapping("/api/tenant-settings")
@RequiredArgsConstructor
public class TenantSettingsController {

    private final TenantSettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE_OWNER')")
    @Operation(summary = "NFR-7.6b: SLA hours per priority and the discount-approval threshold (with defaults)")
    public Response<SettingsRes> get(@AuthenticationPrincipal User currentUser) {
        return Response.ok(settingsService.get(currentUser));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "NFR-7.6b: set them (null = use the default); applies to new tickets and quotation edits")
    public Response<SettingsRes> update(@AuthenticationPrincipal User currentUser, @Valid @RequestBody SettingsReq dto) {
        return Response.ok("Settings saved", settingsService.update(currentUser, dto));
    }
}
