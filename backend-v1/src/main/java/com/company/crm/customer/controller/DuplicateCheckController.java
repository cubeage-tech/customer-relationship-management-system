package com.company.crm.customer.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.customer.service.DuplicateCheckService;
import com.company.crm.customer.service.DuplicateCheckService.CheckReq;
import com.company.crm.customer.service.DuplicateCheckService.Match;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** VAL-1/VAL-3: possible duplicates of a customer, lead or contact being entered (POST so emails stay out of URLs and logs). */
@RestController
@RequestMapping("/api/duplicates")
@RequiredArgsConstructor
public class DuplicateCheckController {

    private final DuplicateCheckService duplicateCheckService;

    @PostMapping("/check")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "VAL-1/VAL-3: records of this tenant with the same email or company name (a warning; nothing is blocked)")
    public Response<List<Match>> check(@AuthenticationPrincipal User currentUser, @RequestBody CheckReq request) {
        return Response.ok(duplicateCheckService.check(currentUser, request));
    }
}
