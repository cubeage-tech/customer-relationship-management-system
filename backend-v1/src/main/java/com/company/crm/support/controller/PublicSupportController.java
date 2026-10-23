package com.company.crm.support.controller;

import com.company.crm.common.response.Response;
import com.company.crm.support.dto.request.ServiceTicketFeedbackReqDto;
import com.company.crm.support.dto.request.SupportRequestReqDto;
import com.company.crm.support.dto.response.PublicFeedbackDto;
import com.company.crm.support.service.ServiceTicketService;
import com.company.crm.support.service.SupportFormService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Customer-facing service pages, opened from links — public by design; the unguessable token in
 * the path is the credential (see Security and EndpointAuthorizationCoverageTest).
 * FR-6.1: the tenant's "Report an issue" form. FR-6.5: rating a resolved ticket.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicSupportController {

    private final SupportFormService supportFormService;
    private final ServiceTicketService ticketService;

    @GetMapping("/support/{token}")
    @Operation(summary = "FR-6.1: the support form's company name")
    public Response<Map<String, String>> supportForm(@PathVariable String token) {
        return Response.ok(supportFormService.formInfo(token));
    }

    @PostMapping("/support/{token}")
    @Operation(summary = "FR-6.1: a customer raises a service request (email must be on record)")
    public Response<Map<String, Object>> submitSupportRequest(@PathVariable String token, @Valid @RequestBody SupportRequestReqDto dto) {
        return Response.ok("Request received", supportFormService.submit(token, dto));
    }

    @GetMapping("/feedback/{token}")
    @Operation(summary = "FR-6.5: the resolved ticket the customer is asked to rate")
    public Response<PublicFeedbackDto> feedback(@PathVariable String token) {
        return Response.ok(ticketService.feedbackPage(token));
    }

    @PostMapping("/feedback/{token}")
    @Operation(summary = "FR-6.5: the customer's 1-5 rating and comment (once)")
    public Response<PublicFeedbackDto> submitFeedback(@PathVariable String token, @Valid @RequestBody ServiceTicketFeedbackReqDto dto) {
        return Response.ok("Thank you for your feedback", ticketService.submitCustomerFeedback(token, dto));
    }
}
