package com.company.crm.integration.health;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.integration.health.IntegrationHealthService.HealthRes;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** INT-8: the platform admin sees which integrations need re-authorising, and marks them fixed. */
@RestController
@RequestMapping("/api/integration-health")
@RequiredArgsConstructor
public class IntegrationHealthController {

    private final IntegrationHealthService healthService;

    @GetMapping
    @PreAuthorize(RoleExpressions.SUPER_ADMIN)
    @Operation(summary = "INT-8: each integration's credential status and how to re-authorise it")
    public Response<List<HealthRes>> list() {
        return Response.ok(healthService.list());
    }

    @PostMapping("/{integration}/resolve")
    @PreAuthorize(RoleExpressions.SUPER_ADMIN)
    @Operation(summary = "INT-8: mark an integration re-authorised (clears the warning until it fails again)")
    public Response<HealthRes> resolve(@AuthenticationPrincipal User currentUser, @PathVariable String integration) {
        return Response.ok("Marked as re-authorised", healthService.resolve(currentUser, integration));
    }
}
