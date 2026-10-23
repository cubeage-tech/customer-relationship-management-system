package com.company.crm.support.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.support.dto.request.CustomerAssetReqDto;
import com.company.crm.support.dto.response.CustomerAssetResDto;
import com.company.crm.support.service.CustomerAssetService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-6.1: a customer's assets (equipment/products), referenced by service tickets. */
@RestController
@RequestMapping("/api/customers/{customerId}/assets")
@RequiredArgsConstructor
public class CustomerAssetController {

    private static final String EDIT_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'SERVICE_AGENT')";

    private final CustomerAssetService assetService;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-6.1: the customer's assets")
    public Response<List<CustomerAssetResDto>> list(@AuthenticationPrincipal User currentUser, @PathVariable Long customerId) {
        return Response.ok(assetService.list(currentUser, customerId));
    }

    @PostMapping
    @PreAuthorize(EDIT_ROLES)
    @Operation(summary = "FR-6.1: add an asset to the customer")
    public Response<CustomerAssetResDto> create(@AuthenticationPrincipal User currentUser, @PathVariable Long customerId,
                                                @Valid @RequestBody CustomerAssetReqDto dto) {
        return Response.ok("Asset added", assetService.create(currentUser, customerId, dto));
    }

    @PutMapping("/{assetId}")
    @PreAuthorize(EDIT_ROLES)
    @Operation(summary = "FR-6.1: edit an asset")
    public Response<CustomerAssetResDto> update(@AuthenticationPrincipal User currentUser, @PathVariable Long customerId,
                                                @PathVariable Long assetId, @Valid @RequestBody CustomerAssetReqDto dto) {
        return Response.ok("Asset updated", assetService.update(currentUser, customerId, assetId, dto));
    }

    @DeleteMapping("/{assetId}")
    @PreAuthorize(EDIT_ROLES)
    @Operation(summary = "FR-6.1: remove an asset (tickets keep their history)")
    public Response<Void> delete(@AuthenticationPrincipal User currentUser, @PathVariable Long customerId, @PathVariable Long assetId) {
        assetService.delete(currentUser, customerId, assetId);
        return Response.ok("Asset removed", null);
    }
}
