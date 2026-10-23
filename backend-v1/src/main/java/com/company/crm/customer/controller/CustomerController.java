package com.company.crm.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import com.company.crm.customer.dto.response.CustomerSummaryDto;
import com.company.crm.common.response.Response;
import com.company.crm.customer.dto.request.CustomerContactReqDto;
import com.company.crm.customer.dto.request.CustomerReqDto;
import com.company.crm.customer.dto.response.CustomerPurchaseResDto;
import com.company.crm.customer.dto.response.CustomerResDto;
import com.company.crm.customer.dto.response.CustomerDocumentResDto;
import com.company.crm.customer.service.CustomerDocumentService;
import com.company.crm.customer.service.CustomerService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import com.company.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.company.crm.common.security.RoleExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module 1 — Customer Management (Customer 360°).
 * Every endpoint is tenant-scoped in the service layer; super_admin (no tenant) has no access.
 * Roles mirror frontend/core/constants/permission.constant.js's CUSTOMERS_* entries:
 * view = every tenant role, create/archive = admin + sales_manager, edit = + sales_executive (own only).
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerDocumentService customerDocumentService;

    @GetMapping("/summary")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "Customer counts for dashboards (total, active, new this quarter) within your data scope")
    public Response<CustomerSummaryDto> getSummary(@AuthenticationPrincipal User currentUser) {
        return Response.ok(customerService.getSummary(currentUser));
    }

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    public Response<List<CustomerResDto>> listCustomers(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return Response.ok(customerService.listCustomers(currentUser, industry, status, search));
    }

    @GetMapping("/{customerId}")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    public Response<CustomerResDto> getCustomer(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId) {
        return Response.ok(customerService.getCustomer(currentUser, customerId));
    }

    @GetMapping("/{customerId}/purchases")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "Purchase history (won deals and customer-approved quotations), newest first")
    public Response<List<CustomerPurchaseResDto>> listPurchases(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId) {
        return Response.ok(customerService.listPurchases(currentUser, customerId));
    }

    @GetMapping("/{customerId}/documents")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-1.5: documents attached to the customer, newest first")
    public Response<List<CustomerDocumentResDto>> listDocuments(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId) {
        return Response.ok(customerDocumentService.list(currentUser, customerId));
    }

    @PostMapping(value = "/{customerId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    @Operation(summary = "FR-1.5: upload a PDF, DOCX, XLSX, JPG or PNG (max 25 MB) to the customer")
    public Response<CustomerDocumentResDto> uploadDocument(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @RequestParam("file") MultipartFile file) {
        return Response.ok("Document uploaded", customerDocumentService.upload(currentUser, customerId, file));
    }

    @GetMapping("/{customerId}/documents/{documentId}/download")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-1.5: download (or, with inline=true, view a PDF/image) a customer document")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "false") boolean inline) {
        CustomerDocumentService.DocumentDownload doc = customerDocumentService.open(currentUser, customerId, documentId);
        boolean viewable = inline && (doc.contentType().equals("application/pdf") || doc.contentType().startsWith("image/"));
        ContentDisposition disposition = ContentDisposition.builder(viewable ? "inline" : "attachment")
                .filename(doc.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.contentType()))
                .contentLength(doc.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(doc.content()));
    }

    @DeleteMapping("/{customerId}/documents/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    @Operation(summary = "FR-1.5: delete a customer document")
    public Response<Void> deleteDocument(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @PathVariable Long documentId) {
        customerDocumentService.delete(currentUser, customerId, documentId);
        return Response.ok("Document deleted", null);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public Response<CustomerResDto> createCustomer(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CustomerReqDto dto) {
        return Response.ok("Customer created", customerService.createCustomer(currentUser, dto));
    }

    @PutMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<CustomerResDto> updateCustomer(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerReqDto dto) {
        return Response.ok("Customer updated", customerService.updateCustomer(currentUser, customerId, dto));
    }

    @PatchMapping("/{customerId}/archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public Response<CustomerResDto> archiveCustomer(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId) {
        return Response.ok("Customer archived", customerService.archiveCustomer(currentUser, customerId));
    }

    @PatchMapping("/{customerId}/restore")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public Response<CustomerResDto> restoreCustomer(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId) {
        return Response.ok("Customer restored", customerService.restoreCustomer(currentUser, customerId));
    }

    @PostMapping("/{customerId}/contacts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<CustomerResDto> addContact(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerContactReqDto dto) {
        return Response.ok("Contact added", customerService.addContact(currentUser, customerId, dto));
    }

    @PutMapping("/{customerId}/contacts/{contactId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<CustomerResDto> updateContact(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @PathVariable Long contactId,
            @Valid @RequestBody CustomerContactReqDto dto) {
        return Response.ok("Contact updated", customerService.updateContact(currentUser, customerId, contactId, dto));
    }

    @DeleteMapping("/{customerId}/contacts/{contactId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<CustomerResDto> deleteContact(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @PathVariable Long contactId) {
        return Response.ok("Contact removed", customerService.deleteContact(currentUser, customerId, contactId));
    }
}
