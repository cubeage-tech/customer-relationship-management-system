package com.company.crm.common.audit;

import com.company.crm.common.response.Response;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** NFR-7.2d: the audit trail of changes to customer, financial and user-management records. */
@RestController
@RequestMapping("/api/audit-events")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'EXECUTIVE_OWNER')")
    @Operation(summary = "NFR-7.2d: audit events, newest first — own tenant (super admin: all, or tenantId)")
    public Response<AuditLogService.Page> search(@AuthenticationPrincipal User currentUser,
                                                 @RequestParam(required = false) Long tenantId,
                                                 @RequestParam(required = false) String entityType,
                                                 @RequestParam(required = false) String action,
                                                 @RequestParam(required = false) Long actorId,
                                                 @RequestParam(required = false) Long entityId,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        return Response.ok(auditLogService.search(currentUser,
                new AuditLogService.Filters(tenantId, entityType, action, actorId, entityId, from, to, page, size)));
    }
}
