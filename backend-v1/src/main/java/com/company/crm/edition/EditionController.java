package com.company.crm.edition;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.edition.EditionService.Applied;
import com.company.crm.edition.EditionService.EditionsRes;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** BIZ-2: the industry editions and the tenant's choice. */
@RestController
@RequestMapping("/api/editions")
@RequiredArgsConstructor
public class EditionController {

    private final EditionService service;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "BIZ-2: the industry editions and which one this tenant uses")
    public Response<EditionsRes> list(@AuthenticationPrincipal User currentUser) {
        return Response.ok(service.list(currentUser));
    }

    @PutMapping("/current")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "BIZ-2: choose an edition — adds its industry fields and pipeline stages (nothing is removed)")
    public Response<Applied> apply(@AuthenticationPrincipal User currentUser, @RequestBody Map<String, String> body) {
        return Response.ok("Edition applied", service.apply(currentUser, body.get("edition")));
    }
}
