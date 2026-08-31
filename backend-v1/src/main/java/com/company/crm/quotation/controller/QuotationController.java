package com.company.crm.quotation.controller;

import com.company.crm.common.response.Response;
import com.company.crm.quotation.dto.request.DiscountReviewReqDto;
import com.company.crm.quotation.dto.request.QuotationCustomerStatusReqDto;
import com.company.crm.quotation.dto.request.QuotationReqDto;
import com.company.crm.quotation.dto.response.QuotationResDto;
import com.company.crm.quotation.service.QuotationService;
import com.company.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module 4 — Quotation & Proposal Management.
 * Every endpoint is tenant-scoped in the service layer; super_admin (no tenant) has no access.
 * Roles mirror frontend/core/constants/permission.constant.js's QUOTATIONS_* entries:
 * view = admin + sales_manager + sales_executive + finance_approver + executive_owner,
 * create/edit/send = admin + sales_manager + sales_executive (own only),
 * discount approval (BR-3) = admin + sales_manager (QUOTATIONS_APPROVE) + finance_approver (QUOTATIONS_APPROVE_DISCOUNT).
 */
@RestController
@RequestMapping("/api/quotations")
@RequiredArgsConstructor
public class QuotationController {

    private final QuotationService quotationService;

    private static final String VIEW_ROLES =
            "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'FINANCE_APPROVER', 'EXECUTIVE_OWNER')";
    private static final String EDIT_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')";
    private static final String APPROVE_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'FINANCE_APPROVER')";

    @GetMapping
    @PreAuthorize(VIEW_ROLES)
    public Response<List<QuotationResDto>> listQuotations(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String search) {
        return Response.ok(quotationService.listQuotations(currentUser, status, customerId, search));
    }

    @GetMapping("/{quotationId}")
    @PreAuthorize(VIEW_ROLES)
    public Response<QuotationResDto> getQuotation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long quotationId) {
        return Response.ok(quotationService.getQuotation(currentUser, quotationId));
    }

    @GetMapping("/{quotationId}/pdf")
    @PreAuthorize(VIEW_ROLES)
    public ResponseEntity<byte[]> downloadPdf(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long quotationId) {
        byte[] pdf = quotationService.generatePdf(currentUser, quotationId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("quotation-" + quotationId + ".pdf").build().toString())
                .body(pdf);
    }

    @PostMapping
    @PreAuthorize(EDIT_ROLES)
    public Response<QuotationResDto> createQuotation(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody QuotationReqDto dto) {
        return Response.ok("Quotation created", quotationService.createQuotation(currentUser, dto));
    }

    @PutMapping("/{quotationId}")
    @PreAuthorize(EDIT_ROLES)
    public Response<QuotationResDto> updateQuotation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long quotationId,
            @Valid @RequestBody QuotationReqDto dto) {
        return Response.ok("Quotation updated", quotationService.updateQuotation(currentUser, quotationId, dto));
    }

    @PatchMapping("/{quotationId}/send")
    @PreAuthorize(EDIT_ROLES)
    public Response<QuotationResDto> send(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long quotationId) {
        return Response.ok("Quotation sent", quotationService.send(currentUser, quotationId));
    }

    @PatchMapping("/{quotationId}/customer-status")
    @PreAuthorize(EDIT_ROLES)
    public Response<QuotationResDto> recordCustomerStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long quotationId,
            @Valid @RequestBody QuotationCustomerStatusReqDto dto) {
        return Response.ok("Customer response recorded", quotationService.recordCustomerStatus(currentUser, quotationId, dto));
    }

    @PatchMapping("/{quotationId}/discount/approve")
    @PreAuthorize(APPROVE_ROLES)
    public Response<QuotationResDto> approveDiscount(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long quotationId) {
        return Response.ok("Discount approved", quotationService.approveDiscount(currentUser, quotationId));
    }

    @PatchMapping("/{quotationId}/discount/reject")
    @PreAuthorize(APPROVE_ROLES)
    public Response<QuotationResDto> rejectDiscount(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long quotationId,
            @RequestBody(required = false) DiscountReviewReqDto dto) {
        String reason = dto != null ? dto.getReason() : null;
        return Response.ok("Discount rejected", quotationService.rejectDiscount(currentUser, quotationId, reason));
    }
}
