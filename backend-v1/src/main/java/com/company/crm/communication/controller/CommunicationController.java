package com.company.crm.communication.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.communication.dto.request.CallLogReqDto;
import com.company.crm.communication.dto.request.EmailSendReqDto;
import com.company.crm.communication.dto.request.SmsAlertRuleReqDto;
import com.company.crm.communication.dto.request.WhatsAppSendReqDto;
import com.company.crm.communication.dto.responce.CommunicationResDto;
import com.company.crm.communication.dto.responce.SmsAlertRuleResDto;
import com.company.crm.communication.service.CommunicationService;
import com.company.crm.communication.service.SmsAlertService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module 5 — Communication Management. Reading a timeline follows the caller's access to the
 * customer/lead; sending and logging is for the roles that talk to customers.
 */
@RestController
@RequestMapping("/api/communications")
@RequiredArgsConstructor
public class CommunicationController {

    private static final String SEND_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'SERVICE_AGENT')";

    private final CommunicationService communicationService;
    private final SmsAlertService smsAlertService;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-5.6: chronological timeline (newest first) for a customer, or for a lead")
    public Response<List<CommunicationResDto>> timeline(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long leadId) {
        return Response.ok(communicationService.timeline(currentUser, customerId, leadId));
    }

    @PostMapping("/calls")
    @PreAuthorize(SEND_ROLES)
    @Operation(summary = "FR-5.4: log a call with outcome and notes")
    public Response<CommunicationResDto> logCall(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CallLogReqDto dto) {
        return Response.ok("Call logged", communicationService.logCall(currentUser, dto));
    }

    @PostMapping("/email")
    @PreAuthorize(SEND_ROLES)
    @Operation(summary = "FR-5.1: send an email and log it (status sent or failed)")
    public Response<CommunicationResDto> sendEmail(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody EmailSendReqDto dto) {
        return Response.ok(communicationService.sendEmail(currentUser, dto));
    }

    @PostMapping("/whatsapp")
    @PreAuthorize(SEND_ROLES)
    @Operation(summary = "FR-5.2: send a WhatsApp message and log it; delivery updates arrive by webhook")
    public Response<CommunicationResDto> sendWhatsApp(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody WhatsAppSendReqDto dto) {
        return Response.ok(communicationService.sendWhatsApp(currentUser, dto));
    }

    @GetMapping("/sms-alerts")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-5.3: which events send SMS alerts, and to whom")
    public Response<List<SmsAlertRuleResDto>> listSmsAlerts(@AuthenticationPrincipal User currentUser) {
        return Response.ok(smsAlertService.listRules(currentUser));
    }

    @PutMapping("/sms-alerts")
    @PreAuthorize(RoleExpressions.TENANT_ADMIN)
    @Operation(summary = "FR-5.3: switch SMS alert events on/off and choose recipients")
    public Response<List<SmsAlertRuleResDto>> updateSmsAlerts(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody List<SmsAlertRuleReqDto> rules) {
        return Response.ok("SMS alerts updated", smsAlertService.updateRules(currentUser, rules));
    }
}
