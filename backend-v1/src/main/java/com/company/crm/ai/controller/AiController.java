package com.company.crm.ai.controller;

import com.company.crm.ai.dto.AiDtos.*;
import com.company.crm.ai.llm.LlmGateway;
import com.company.crm.ai.service.AiAssistantService;
import com.company.crm.ai.service.AiWritingService;
import com.company.crm.ai.service.ForecastService;
import com.company.crm.ai.service.LeadScoringService;
import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API-G11 (SRS §10.2): AI services. Lead score and forecast are computed from CRM data; chat,
 * email generation and sentiment use Claude (503 when no ANTHROPIC_API_KEY is configured). Every
 * answer is limited to what the caller may see.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private static final String LEAD_ROLES =
            "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'MARKETING_EXECUTIVE', 'FINANCE_APPROVER', 'EXECUTIVE_OWNER')";
    private static final String FORECAST_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'FINANCE_APPROVER', 'EXECUTIVE_OWNER')";
    private static final String WRITER_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'SERVICE_AGENT', 'MARKETING_EXECUTIVE')";

    private final LeadScoringService leadScoring;
    private final ForecastService forecastService;
    private final AiAssistantService assistant;
    private final AiWritingService writing;
    private final LlmGateway llm;

    @GetMapping("/status")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "API-G11: whether the language-model features are configured")
    public Response<Map<String, Boolean>> status() {
        return Response.ok(Map.of("llmConfigured", llm.isConfigured()));
    }

    @GetMapping("/lead-score")
    @PreAuthorize(LEAD_ROLES)
    @Operation(summary = "FR-AI-1: lead scores (0–100 with factors) for my leads, best first; or one lead with leadId")
    public Response<List<LeadScore>> leadScore(@AuthenticationPrincipal User currentUser,
                                               @RequestParam(required = false) Long leadId,
                                               @RequestParam(required = false) Integer minScore) {
        return Response.ok(leadId != null ? List.of(leadScoring.score(currentUser, leadId)) : leadScoring.scores(currentUser, minScore));
    }

    @GetMapping("/forecast")
    @PreAuthorize(FORECAST_ROLES)
    @Operation(summary = "FR-AI-2: revenue forecast for this/next month or quarter, vs the previous period and targets")
    public Response<Forecast> forecast(@AuthenticationPrincipal User currentUser,
                                       @RequestParam(defaultValue = "next_month") String period) {
        return Response.ok(forecastService.forecast(currentUser, period));
    }

    @PostMapping("/chat")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-AI-4: ask a question about the CRM in plain language; answer + recommended action")
    public Response<ChatRes> chat(@AuthenticationPrincipal User currentUser, @Valid @RequestBody ChatReq dto) {
        return Response.ok(assistant.ask(currentUser, dto.getQuestion().trim()));
    }

    @PostMapping("/generate-email")
    @PreAuthorize(WRITER_ROLES)
    @Operation(summary = "FR-AI-5: draft an email, WhatsApp or SMS for a customer, lead or deal (not sent)")
    public Response<GeneratedMessage> generateEmail(@AuthenticationPrincipal User currentUser, @Valid @RequestBody GenerateReq dto) {
        return Response.ok(writing.generate(currentUser, dto));
    }

    @PostMapping("/sentiment")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-AI-6: sentiment and churn risk of a customer message (text or communicationId)")
    public Response<SentimentRes> sentiment(@AuthenticationPrincipal User currentUser, @Valid @RequestBody SentimentReq dto) {
        return Response.ok(writing.sentiment(currentUser, dto));
    }
}
