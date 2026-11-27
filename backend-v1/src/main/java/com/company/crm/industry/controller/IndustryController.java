package com.company.crm.industry.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.industry.dto.request.IndustryReqDto;
import com.company.crm.industry.dto.response.IndustryResDto;
import com.company.crm.industry.service.IndustryService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-1.6: the tenant's configurable customer industry list. Everyone reads it; the tenant admin edits it. */
@RestController
@RequestMapping("/api/industries")
@RequiredArgsConstructor
public class IndustryController {

    private final IndustryService industryService;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-1.6: customer industries (active only unless includeInactive=true)")
    public Response<List<IndustryResDto>> list(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return Response.ok(industryService.list(currentUser, includeInactive));
    }

    @PostMapping
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-1.6: add an industry to the list")
    public Response<IndustryResDto> create(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody IndustryReqDto dto) {
        return Response.ok("Industry added", industryService.create(currentUser, dto));
    }

    @PutMapping("/{code}")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-1.6: rename or (de)activate an industry; codes never change")
    public Response<IndustryResDto> update(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String code,
            @Valid @RequestBody IndustryReqDto dto) {
        return Response.ok("Industry updated", industryService.update(currentUser, code, dto));
    }
}
