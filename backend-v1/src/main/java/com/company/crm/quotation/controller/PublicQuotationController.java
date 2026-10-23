package com.company.crm.quotation.controller;

import com.company.crm.common.response.Response;
import com.company.crm.quotation.dto.response.PublicQuotationDto;
import com.company.crm.quotation.service.QuotationService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-4.5: the customer's view link. Public by design — the unguessable token is the credential —
 * so there is no {@code @PreAuthorize}; see Security and EndpointAuthorizationCoverageTest.
 */
@RestController
@RequestMapping("/api/public/quotations")
@RequiredArgsConstructor
public class PublicQuotationController {

    private final QuotationService quotationService;

    // A signed-in CRM user previewing the link (currentUser != null) does not count as the customer viewing it.
    @GetMapping("/{token}")
    @Operation(summary = "FR-4.5: customer views a sent quotation (marks it viewed)")
    public Response<PublicQuotationDto> view(@AuthenticationPrincipal User currentUser, @PathVariable String token) {
        return Response.ok(quotationService.viewAsCustomer(token, currentUser == null));
    }

    @GetMapping("/{token}/pdf")
    @Operation(summary = "FR-4.5: customer downloads the quotation PDF")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal User currentUser, @PathVariable String token) {
        QuotationService.CustomerPdf pdf = quotationService.customerPdf(token, currentUser == null);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }
}
