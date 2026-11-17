package com.company.crm.communication.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.communication.dto.request.CommunicationLogReqDto;
import com.company.crm.communication.dto.response.CommunicationLogResDto;
import com.company.crm.communication.service.CommunicationService;
import com.company.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/communications")
@RequiredArgsConstructor
public class CommunicationController {

    private final CommunicationService communicationService;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    public Response<List<CommunicationLogResDto>> getCustomerTimeline(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId) {
        return Response.ok(communicationService.getCustomerTimeline(currentUser, customerId));
    }

    @PostMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    public Response<CommunicationLogResDto> createLog(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @Valid @RequestBody CommunicationLogReqDto dto) {
        return Response.ok("Communication logged",
                communicationService.createLog(currentUser, customerId, dto));
    }
}
