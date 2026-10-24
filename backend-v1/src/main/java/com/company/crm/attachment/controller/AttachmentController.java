package com.company.crm.attachment.controller;

import com.company.crm.attachment.entity.Attachment;
import com.company.crm.attachment.service.AttachmentService;
import com.company.crm.attachment.service.AttachmentService.AttachmentRes;
import com.company.crm.common.response.Response;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
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

/**
 * DB-E16: attachments on quotations and service tickets. The roles match each record's own
 * endpoints (QuotationController, ServiceTicketController); the record's data scope is checked
 * in the service.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttachmentController {

    private static final String QUOTATION_VIEW =
            "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'FINANCE_APPROVER', 'EXECUTIVE_OWNER')";
    private static final String QUOTATION_EDIT = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')";
    private static final String TICKET_VIEW = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SERVICE_AGENT', 'EXECUTIVE_OWNER')";
    private static final String TICKET_EDIT = "hasAnyRole('ADMIN', 'SERVICE_AGENT')";

    private final AttachmentService attachmentService;

    // ==================== Quotations ====================

    @GetMapping("/quotations/{quotationId}/attachments")
    @PreAuthorize(QUOTATION_VIEW)
    @Operation(summary = "DB-E16: files attached to a quotation")
    public Response<List<AttachmentRes>> quotationAttachments(@AuthenticationPrincipal User currentUser, @PathVariable Long quotationId) {
        return Response.ok(attachmentService.list(currentUser, Attachment.QUOTATION, quotationId));
    }

    @PostMapping(value = "/quotations/{quotationId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(QUOTATION_EDIT)
    @Operation(summary = "DB-E16: attach a PDF, DOCX, XLSX, JPG or PNG (max 25 MB) to a quotation")
    public Response<AttachmentRes> uploadQuotationAttachment(@AuthenticationPrincipal User currentUser, @PathVariable Long quotationId,
                                                             @RequestParam("file") MultipartFile file) {
        return Response.ok("File attached", attachmentService.upload(currentUser, Attachment.QUOTATION, quotationId, file));
    }

    @GetMapping("/quotations/{quotationId}/attachments/{attachmentId}/download")
    @PreAuthorize(QUOTATION_VIEW)
    @Operation(summary = "DB-E16: download (inline=true views a PDF/image) a quotation attachment")
    public ResponseEntity<InputStreamResource> downloadQuotationAttachment(@AuthenticationPrincipal User currentUser,
                                                                           @PathVariable Long quotationId, @PathVariable Long attachmentId,
                                                                           @RequestParam(defaultValue = "false") boolean inline) {
        return stream(attachmentService.open(currentUser, Attachment.QUOTATION, quotationId, attachmentId), inline);
    }

    @DeleteMapping("/quotations/{quotationId}/attachments/{attachmentId}")
    @PreAuthorize(QUOTATION_EDIT)
    @Operation(summary = "DB-E16: remove a quotation attachment")
    public Response<Void> deleteQuotationAttachment(@AuthenticationPrincipal User currentUser, @PathVariable Long quotationId,
                                                    @PathVariable Long attachmentId) {
        attachmentService.delete(currentUser, Attachment.QUOTATION, quotationId, attachmentId);
        return Response.ok("Attachment removed", null);
    }

    // ==================== Service tickets ====================

    @GetMapping("/tickets/{ticketId}/attachments")
    @PreAuthorize(TICKET_VIEW)
    @Operation(summary = "DB-E16: files attached to a service ticket")
    public Response<List<AttachmentRes>> ticketAttachments(@AuthenticationPrincipal User currentUser, @PathVariable Long ticketId) {
        return Response.ok(attachmentService.list(currentUser, Attachment.SERVICE_TICKET, ticketId));
    }

    @PostMapping(value = "/tickets/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(TICKET_EDIT)
    @Operation(summary = "DB-E16: attach a PDF, DOCX, XLSX, JPG or PNG (max 25 MB) to a service ticket")
    public Response<AttachmentRes> uploadTicketAttachment(@AuthenticationPrincipal User currentUser, @PathVariable Long ticketId,
                                                          @RequestParam("file") MultipartFile file) {
        return Response.ok("File attached", attachmentService.upload(currentUser, Attachment.SERVICE_TICKET, ticketId, file));
    }

    @GetMapping("/tickets/{ticketId}/attachments/{attachmentId}/download")
    @PreAuthorize(TICKET_VIEW)
    @Operation(summary = "DB-E16: download (inline=true views a PDF/image) a ticket attachment")
    public ResponseEntity<InputStreamResource> downloadTicketAttachment(@AuthenticationPrincipal User currentUser,
                                                                        @PathVariable Long ticketId, @PathVariable Long attachmentId,
                                                                        @RequestParam(defaultValue = "false") boolean inline) {
        return stream(attachmentService.open(currentUser, Attachment.SERVICE_TICKET, ticketId, attachmentId), inline);
    }

    @DeleteMapping("/tickets/{ticketId}/attachments/{attachmentId}")
    @PreAuthorize(TICKET_EDIT)
    @Operation(summary = "DB-E16: remove a ticket attachment")
    public Response<Void> deleteTicketAttachment(@AuthenticationPrincipal User currentUser, @PathVariable Long ticketId,
                                                 @PathVariable Long attachmentId) {
        attachmentService.delete(currentUser, Attachment.SERVICE_TICKET, ticketId, attachmentId);
        return Response.ok("Attachment removed", null);
    }

    /** Same headers as customer documents: only PDFs and images are ever shown inline. */
    private static ResponseEntity<InputStreamResource> stream(AttachmentService.Download file, boolean inline) {
        boolean viewable = inline && (file.contentType().equals("application/pdf") || file.contentType().startsWith("image/"));
        ContentDisposition disposition = ContentDisposition.builder(viewable ? "inline" : "attachment")
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.content()));
    }
}
