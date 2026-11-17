package com.company.crm.customer.document;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/documents")
@RequiredArgsConstructor
public class CustomerDocumentController {

    private static final String MANAGE_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')";

    private final CustomerDocumentService documentService;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    public Response<List<CustomerDocumentResDto>> list(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId) {
        return Response.ok(documentService.list(currentUser, customerId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE_ROLES)
    public Response<CustomerDocumentResDto> upload(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @RequestParam("file") MultipartFile file) {
        return Response.ok("Customer document uploaded",
                documentService.upload(currentUser, customerId, file));
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @PathVariable Long documentId) {
        CustomerDocumentService.CustomerDocumentDownload document =
                documentService.download(currentUser, customerId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .contentLength(document.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(document.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(document.content());
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize(MANAGE_ROLES)
    public Response<Void> delete(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @PathVariable Long documentId) {
        documentService.delete(currentUser, customerId, documentId);
        return Response.ok("Customer document deleted", null);
    }
}
