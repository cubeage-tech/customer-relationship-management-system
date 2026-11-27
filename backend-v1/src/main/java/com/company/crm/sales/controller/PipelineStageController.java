package com.company.crm.sales.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.sales.dto.request.PipelineStageReqDto;
import com.company.crm.sales.dto.response.PipelineStageResDto;
import com.company.crm.sales.service.PipelineStageService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-3.2: the tenant's configurable sales stages. Everyone reads them; the tenant admin edits them. */
@RestController
@RequestMapping("/api/opportunity-stages")
@RequiredArgsConstructor
public class PipelineStageController {

    private final PipelineStageService stageService;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-3.2: sales stages in pipeline order (active only unless includeInactive=true)")
    public Response<List<PipelineStageResDto>> list(@AuthenticationPrincipal User currentUser,
                                                    @RequestParam(defaultValue = "false") boolean includeInactive) {
        return Response.ok(stageService.list(currentUser, includeInactive));
    }

    @PostMapping
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-3.2: add an open stage (placed before Won/Lost)")
    public Response<PipelineStageResDto> create(@AuthenticationPrincipal User currentUser, @Valid @RequestBody PipelineStageReqDto dto) {
        return Response.ok("Stage added", stageService.create(currentUser, dto));
    }

    @PutMapping("/{code}")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-3.2: rename or (de)activate a stage; Won/Lost can only be renamed")
    public Response<PipelineStageResDto> update(@AuthenticationPrincipal User currentUser, @PathVariable String code,
                                                @Valid @RequestBody PipelineStageReqDto dto) {
        return Response.ok("Stage updated", stageService.update(currentUser, code, dto));
    }

    @PutMapping("/order")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-3.2: reorder the open stages (list every open stage code once)")
    public Response<List<PipelineStageResDto>> reorder(@AuthenticationPrincipal User currentUser, @RequestBody List<String> codes) {
        return Response.ok("Stages reordered", stageService.reorder(currentUser, codes));
    }
}
