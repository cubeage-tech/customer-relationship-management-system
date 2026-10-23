package com.company.crm.branding.controller;

import com.company.crm.branding.dto.BrandingReqDto;
import com.company.crm.branding.dto.BrandingResDto;
import com.company.crm.branding.service.BrandingService;
import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** AC-4.2: company branding for quotation PDFs. Everyone in the tenant reads it; the admin edits it. */
@RestController
@RequestMapping("/api/branding")
@RequiredArgsConstructor
public class BrandingController {

    private final BrandingService brandingService;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "AC-4.2: the tenant's quotation branding")
    public Response<BrandingResDto> get(@AuthenticationPrincipal User currentUser) {
        return Response.ok(brandingService.get(currentUser));
    }

    @PutMapping
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "AC-4.2: update company details and brand colour")
    public Response<BrandingResDto> update(@AuthenticationPrincipal User currentUser, @Valid @RequestBody BrandingReqDto dto) {
        return Response.ok("Branding saved", brandingService.update(currentUser, dto));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "AC-4.2: upload the company logo (PNG or JPG, max 1 MB)")
    public Response<BrandingResDto> uploadLogo(@AuthenticationPrincipal User currentUser, @RequestParam("file") MultipartFile file) {
        return Response.ok("Logo uploaded", brandingService.uploadLogo(currentUser, file));
    }

    @DeleteMapping("/logo")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "AC-4.2: remove the company logo")
    public Response<BrandingResDto> removeLogo(@AuthenticationPrincipal User currentUser) {
        return Response.ok("Logo removed", brandingService.removeLogo(currentUser));
    }

    @GetMapping("/logo")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "AC-4.2: the company logo image (404 when none)")
    public ResponseEntity<byte[]> logo(@AuthenticationPrincipal User currentUser) {
        BrandingService.Logo logo = brandingService.logo(currentUser);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(logo.content());
    }
}
