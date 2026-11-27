package com.company.crm.integration.controller;

import com.company.crm.common.response.Response;
import com.company.crm.integration.dto.IntegrationDtos.CreatedKey;
import com.company.crm.integration.dto.IntegrationDtos.KeyReq;
import com.company.crm.integration.dto.IntegrationDtos.KeyRes;
import com.company.crm.integration.service.IntegrationKeyService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API-G12: the tenant admin manages the API keys its ERP uses. */
@RestController
@RequestMapping("/api/integration-keys")
@RequiredArgsConstructor
public class IntegrationKeyController {

    private final IntegrationKeyService keyService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "API-G12: the tenant's ERP API keys (never the keys themselves)")
    public Response<List<KeyRes>> list(@AuthenticationPrincipal User currentUser) {
        return Response.ok(keyService.list(currentUser));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "API-G12: create an ERP API key — the key is returned once, store it in the ERP")
    public Response<CreatedKey> create(@AuthenticationPrincipal User currentUser, @Valid @RequestBody KeyReq dto) {
        return Response.ok("API key created", keyService.create(currentUser, dto));
    }

    @DeleteMapping("/{keyId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "API-G12: revoke an ERP API key (stops working immediately)")
    public Response<Void> revoke(@AuthenticationPrincipal User currentUser, @PathVariable Long keyId) {
        keyService.revoke(currentUser, keyId);
        return Response.ok("API key revoked", null);
    }
}
