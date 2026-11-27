package com.company.crm.ai.controller;

import com.company.crm.ai.service.CoachingShareService;
import com.company.crm.ai.service.CoachingShareService.ShareReq;
import com.company.crm.ai.service.CoachingShareService.SharedCoaching;
import com.company.crm.common.response.Response;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** FR-AI-8.3: coaching shared between a salesperson and their manager. */
@RestController
@RequestMapping("/api/coaching")
@RequiredArgsConstructor
public class CoachingController {

    /** Same roles that see sales performance and coaching (FR-AI-8.1/8.2). */
    private static final String COACHING_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'EXECUTIVE_OWNER')";

    private final CoachingShareService coaching;

    @GetMapping
    @PreAuthorize(COACHING_ROLES)
    @Operation(summary = "FR-AI-8.3: coaching shared with a salesperson (me by default), newest first")
    public Response<List<SharedCoaching>> history(@AuthenticationPrincipal User currentUser, @RequestParam(required = false) Long userId) {
        return Response.ok(coaching.history(currentUser, userId));
    }

    @PostMapping
    @PreAuthorize(COACHING_ROLES)
    @Operation(summary = "FR-AI-8.3: share a coaching plan — with the salesperson, or (my own) with my manager — with a note")
    public Response<SharedCoaching> share(@AuthenticationPrincipal User currentUser, @RequestBody ShareReq req) {
        return Response.ok("Coaching shared", coaching.share(currentUser, req));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize(COACHING_ROLES)
    @Operation(summary = "FR-AI-8.3: the salesperson acknowledges shared coaching, optionally replying")
    public Response<SharedCoaching> acknowledge(@AuthenticationPrincipal User currentUser, @PathVariable Long id,
                                                @RequestBody(required = false) Map<String, String> body) {
        return Response.ok(coaching.acknowledge(currentUser, id, body != null ? body.get("reply") : null));
    }
}
