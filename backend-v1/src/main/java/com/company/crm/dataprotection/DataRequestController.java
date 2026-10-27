package com.company.crm.dataprotection;

import com.company.crm.common.response.Response;
import com.company.crm.dataprotection.DataRequestService.CreateReq;
import com.company.crm.dataprotection.DataRequestService.DataRequestRes;
import com.company.crm.dataprotection.DataRequestService.ExportFile;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.Map;

/** SEC-8: the tenant admin logs and carries out data-protection requests (export / erasure). */
@RestController
@RequestMapping("/api/data-requests")
@RequiredArgsConstructor
public class DataRequestController {

    private final DataRequestService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "SEC-8: data-protection requests, newest first, with their due dates")
    public Response<List<DataRequestRes>> list(@AuthenticationPrincipal User currentUser) {
        return Response.ok(service.list(currentUser));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "SEC-8: log a request to export or erase a customer's or lead's personal data (due in 30 days)")
    public Response<DataRequestRes> create(@AuthenticationPrincipal User currentUser, @Valid @RequestBody CreateReq req) {
        return Response.ok("Request logged", service.create(currentUser, req));
    }

    @GetMapping("/{requestId}/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "SEC-8: download everything held about the subject as JSON (completes an export request)")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal User currentUser, @PathVariable Long requestId) {
        ExportFile file = service.export(currentUser, requestId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    @PostMapping("/{requestId}/erase")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "SEC-8: erase the subject's personal data (body {\"confirm\":\"ERASE\"}); cannot be undone")
    public Response<DataRequestRes> erase(@AuthenticationPrincipal User currentUser, @PathVariable Long requestId,
                                          @RequestBody Map<String, String> body) {
        return Response.ok("Personal data erased", service.erase(currentUser, requestId, body.get("confirm")));
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "SEC-8: close a request without acting on it (with the reason)")
    public Response<DataRequestRes> reject(@AuthenticationPrincipal User currentUser, @PathVariable Long requestId,
                                           @RequestBody Map<String, String> body) {
        return Response.ok("Request closed", service.reject(currentUser, requestId, body.get("reason")));
    }
}
