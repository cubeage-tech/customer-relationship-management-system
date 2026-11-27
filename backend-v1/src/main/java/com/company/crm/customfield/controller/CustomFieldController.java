package com.company.crm.customfield.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.customfield.dto.CustomFieldDtos.ApplyResult;
import com.company.crm.customfield.dto.CustomFieldDtos.DefinitionReq;
import com.company.crm.customfield.dto.CustomFieldDtos.DefinitionRes;
import com.company.crm.customfield.dto.CustomFieldDtos.Template;
import com.company.crm.customfield.dto.CustomFieldDtos.ValuesRes;
import com.company.crm.customfield.service.CustomFieldService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** ARC-9: industry-specific custom fields on customers, leads and opportunities. */
@RestController
@RequestMapping("/api/custom-fields")
@RequiredArgsConstructor
public class CustomFieldController {

    private final CustomFieldService service;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "ARC-9: active custom fields of a record type (customer, lead or opportunity), every industry's")
    public Response<List<DefinitionRes>> active(@AuthenticationPrincipal User currentUser, @RequestParam String entityType) {
        return Response.ok(service.active(currentUser, entityType));
    }

    @GetMapping("/manage")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ARC-9: every custom field, including switched-off ones")
    public Response<List<DefinitionRes>> all(@AuthenticationPrincipal User currentUser) {
        return Response.ok(service.all(currentUser));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ARC-9: add a custom field")
    public Response<DefinitionRes> create(@AuthenticationPrincipal User currentUser, @Valid @RequestBody DefinitionReq req) {
        return Response.ok("Custom field added", service.create(currentUser, req));
    }

    @PutMapping("/{fieldId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ARC-9: edit a custom field (label, choices, required, industry, order, on/off)")
    public Response<DefinitionRes> update(@AuthenticationPrincipal User currentUser, @PathVariable Long fieldId,
                                          @Valid @RequestBody DefinitionReq req) {
        return Response.ok("Custom field updated", service.update(currentUser, fieldId, req));
    }

    @GetMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ARC-9: built-in industry templates")
    public Response<List<Template>> templates() {
        return Response.ok(service.templates());
    }

    @PostMapping("/templates/{industry}/apply")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ARC-9: add an industry template's fields (existing fields are kept)")
    public Response<ApplyResult> applyTemplate(@AuthenticationPrincipal User currentUser, @PathVariable String industry) {
        return Response.ok("Template applied", service.applyTemplate(currentUser, industry));
    }

    @GetMapping("/values/{entityType}/{recordId}")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "ARC-9: a record's custom fields and values (same access as the record)")
    public Response<ValuesRes> values(@AuthenticationPrincipal User currentUser, @PathVariable String entityType,
                                      @PathVariable Long recordId) {
        return Response.ok(service.values(currentUser, entityType, recordId));
    }

    @PutMapping("/values/{entityType}/{recordId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    @Operation(summary = "ARC-9: save a record's custom field values (checked against each field's type)")
    public Response<ValuesRes> save(@AuthenticationPrincipal User currentUser, @PathVariable String entityType,
                                    @PathVariable Long recordId, @RequestBody Map<String, Object> values) {
        return Response.ok("Custom fields saved", service.save(currentUser, entityType, recordId, values));
    }
}
