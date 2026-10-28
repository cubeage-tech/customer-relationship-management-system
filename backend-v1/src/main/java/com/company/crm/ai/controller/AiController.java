package com.company.crm.ai.controller;

import com.company.crm.ai.dto.AiDtos.ContactTime;
import com.company.crm.ai.dto.AiDtos.SalesApproach;
import com.company.crm.ai.dto.AiDtos.TalkingPointsReq;
import com.company.crm.ai.service.ContactTimeService;
import com.company.crm.ai.service.SalesCoachService;
import com.company.crm.ai.dto.AiDtos.DealProbability;
import com.company.crm.ai.dto.AiDtos.ProductRecs;
import com.company.crm.ai.service.DealProbabilityService;
import com.company.crm.ai.service.ProductRecommendationService;
import java.util.Objects;
import com.company.crm.ai.perf.AiLatency;
import com.company.crm.ai.service.PriceRecommendationService;
import jakarta.validation.constraints.Pattern;
import com.company.crm.ai.service.AiQuotationDraftService;
import com.company.crm.ai.service.AiContentService;
import com.company.crm.ai.service.ChurnRiskService;
import com.company.crm.ai.privacy.AiPrivacyService;
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
    private static final String CHURN_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'EXECUTIVE_OWNER')";
    private static final String WRITER_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'SERVICE_AGENT', 'MARKETING_EXECUTIVE')";

    private final LeadScoringService leadScoring;
    private final ForecastService forecastService;
    private final AiAssistantService assistant;
    private final AiWritingService writing;
    private final LlmGateway llm;
    private final AiPrivacyService privacy;
    private final AiQuotationDraftService quotationDrafts;
    private final PriceRecommendationService priceRecommendations;
    private final AiLatency latency;
    private final DealProbabilityService dealProbabilities;
    private final ProductRecommendationService productRecommendations;
    private final ContactTimeService contactTimes;
    private final SalesCoachService salesCoach;
    private final AiContentService content;
    private final ChurnRiskService churnRisk;

    @GetMapping("/status")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "API-G11 / SEC-6: whether the language-model features are configured and switched on for the tenant")
    public Response<Map<String, Object>> status(@AuthenticationPrincipal User currentUser) {
        AiPrivacyService.AiSettings settings = privacy.settings(currentUser);
        return Response.ok(Map.of("llmConfigured", llm.isConfigured() && settings.aiEnabled(), "providerConfigured", llm.isConfigured(),
                "provider", Objects.requireNonNullElse(llm.provider(), "none"),
                "aiEnabled", settings.aiEnabled(), "messageContent", settings.messageContent(), "policy", AiPrivacyService.POLICY));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "SEC-5/SEC-6: the tenant's AI switches (AI on/off, message text allowed) and what each feature sends")
    public Response<Map<String, Object>> settings(@AuthenticationPrincipal User currentUser) {
        return Response.ok(Map.of("settings", privacy.settings(currentUser), "policy", AiPrivacyService.POLICY));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "SEC-5/SEC-6: switch AI off, or keep message text away from the AI provider (audited)")
    public Response<AiPrivacyService.AiSettings> updateSettings(@AuthenticationPrincipal User currentUser,
                                                                @RequestBody AiPrivacyService.AiSettings req) {
        return Response.ok("AI settings saved", privacy.updateSettings(currentUser, req));
    }

    @GetMapping("/exclusions/{recordType}/{recordId}")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "SEC-6: whether a customer or lead is excluded from AI features")
    public Response<AiPrivacyService.Exclusion> exclusion(@AuthenticationPrincipal User currentUser,
                                                          @PathVariable @Pattern(regexp = "customer|lead") String recordType,
                                                          @PathVariable Long recordId) {
        return Response.ok(privacy.exclusion(currentUser, recordType, recordId));
    }

    @PutMapping("/exclusions/{recordType}/{recordId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    @Operation(summary = "SEC-6: exclude a customer or lead from AI features (or include it again); audited")
    public Response<AiPrivacyService.Exclusion> setExclusion(@AuthenticationPrincipal User currentUser,
                                                             @PathVariable @Pattern(regexp = "customer|lead") String recordType,
                                                             @PathVariable Long recordId, @RequestBody Map<String, Boolean> body) {
        return Response.ok(privacy.setExcluded(currentUser, recordType, recordId, Boolean.TRUE.equals(body.get("excluded"))));
    }

    @PostMapping("/quotation-draft")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    @Operation(summary = "NAV-2: a first draft of a quotation (line items from your products, cover note) to edit before saving")
    public Response<AiQuotationDraftService.QuotationDraft> quotationDraft(@AuthenticationPrincipal User currentUser,
                                                                           @Valid @RequestBody AiQuotationDraftService.DraftReq req) {
        return Response.ok(latency.time("quotation_draft", AiLatency.Kind.GENERATIVE, () -> quotationDrafts.draft(currentUser, req)));
    }

    @PostMapping("/price-recommendations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    @Operation(summary = "FR-4.7: recommended discount and price per product (optionally for one customer) from past quotes won and lost")
    public Response<List<PriceRecommendationService.PriceRec>> priceRecommendations(@AuthenticationPrincipal User currentUser,
                                                                                    @Valid @RequestBody PriceRecommendationService.PriceRecReq req) {
        return Response.ok(latency.time("price_recommendation", AiLatency.Kind.STATISTICAL, () -> priceRecommendations.recommend(currentUser, req)));
    }

    @GetMapping("/deal-probabilities")
    @PreAuthorize(FORECAST_ROLES)
    @Operation(summary = "FR-AI-2.2: close probability of each open deal I can see (likeliest first), or one deal, with the reasons")
    public Response<List<DealProbability>> dealProbabilities(@AuthenticationPrincipal User currentUser,
                                                             @RequestParam(required = false) Long opportunityId) {
        return Response.ok(latency.time("deal_probability", AiLatency.Kind.STATISTICAL, () -> opportunityId != null
                ? List.of(dealProbabilities.probability(currentUser, opportunityId)) : dealProbabilities.probabilities(currentUser)));
    }

    @GetMapping("/product-recommendations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'MARKETING_EXECUTIVE', 'EXECUTIVE_OWNER')")
    @Operation(summary = "FR-AI-3.1: complementary, next and re-order products for a customer from purchase history, with the reasons")
    public Response<ProductRecs> productRecommendations(@AuthenticationPrincipal User currentUser, @RequestParam Long customerId) {
        return Response.ok(latency.time("product_recommendation", AiLatency.Kind.STATISTICAL,
                () -> productRecommendations.recommend(currentUser, customerId)));
    }

    @GetMapping("/contact-time")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-AI-3.2: the best weekday/time windows to contact a customer or lead, from when they have engaged")
    public Response<ContactTime> contactTime(@AuthenticationPrincipal User currentUser, @RequestParam(required = false) Long customerId,
                                             @RequestParam(required = false) Long leadId) {
        return Response.ok(latency.time("contact_time", AiLatency.Kind.STATISTICAL, () -> contactTimes.bestTime(currentUser, customerId, leadId)));
    }

    @PostMapping("/talking-points")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    @Operation(summary = "FR-AI-3.3: a suggested sales approach — talking points, questions, objections — for a customer, lead or deal")
    public Response<SalesApproach> talkingPoints(@AuthenticationPrincipal User currentUser, @RequestBody TalkingPointsReq req) {
        return Response.ok(latency.time("talking_points", AiLatency.Kind.GENERATIVE, () -> salesCoach.talkingPoints(currentUser, req)));
    }

    @GetMapping("/performance")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "NFR-7.1c: response times of each AI feature on this server (p50/p95) against their 2 s / 8 s budgets")
    public Response<List<AiLatency.FeatureLatency>> performance() {
        return Response.ok(latency.snapshot());
    }

    @GetMapping("/lead-score")
    @PreAuthorize(LEAD_ROLES)
    @Operation(summary = "FR-AI-1: lead scores (0–100 with factors) for my leads, best first; or one lead with leadId")
    public Response<List<LeadScore>> leadScore(@AuthenticationPrincipal User currentUser,
                                               @RequestParam(required = false) Long leadId,
                                               @RequestParam(required = false) Integer minScore) {
        return Response.ok(latency.time("lead_score", AiLatency.Kind.STATISTICAL,
                () -> leadId != null ? List.of(leadScoring.score(currentUser, leadId)) : leadScoring.scores(currentUser, minScore)));
    }

    @GetMapping("/forecast")
    @PreAuthorize(FORECAST_ROLES)
    @Operation(summary = "FR-AI-2: revenue forecast for this/next month or quarter, vs the previous period and targets")
    public Response<Forecast> forecast(@AuthenticationPrincipal User currentUser,
                                       @RequestParam(defaultValue = "next_month") String period) {
        return Response.ok(latency.time("forecast", AiLatency.Kind.STATISTICAL, () -> forecastService.forecast(currentUser, period)));
    }

    @PostMapping("/chat")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-AI-4: ask a question about the CRM in plain language; answer + recommended action")
    public Response<ChatRes> chat(@AuthenticationPrincipal User currentUser, @Valid @RequestBody ChatReq dto) {
        return Response.ok(latency.time("assistant_chat", AiLatency.Kind.GENERATIVE,
                () -> assistant.ask(currentUser, dto.getQuestion().trim(), dto.getHistory())));
    }

    @PostMapping("/generate-email")
    @PreAuthorize(WRITER_ROLES)
    @Operation(summary = "FR-AI-5: draft an email, WhatsApp or SMS for a customer, lead or deal (not sent)")
    public Response<GeneratedMessage> generateEmail(@AuthenticationPrincipal User currentUser, @Valid @RequestBody GenerateReq dto) {
        return Response.ok(latency.time("message_writing", AiLatency.Kind.GENERATIVE, () -> writing.generate(currentUser, dto)));
    }

    @PostMapping("/campaign-copy")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING_EXECUTIVE')")
    @Operation(summary = "FR-AI-5.3: draft the message of an email, SMS or WhatsApp campaign for its audience (not saved)")
    public Response<AiContentService.CampaignCopy> campaignCopy(@AuthenticationPrincipal User currentUser,
                                                                @Valid @RequestBody AiContentService.CampaignCopyReq req) {
        return Response.ok(latency.time("campaign_copy", AiLatency.Kind.GENERATIVE, () -> content.campaignCopy(currentUser, req)));
    }

    @PostMapping("/proposal")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    @Operation(summary = "FR-AI-5.3: draft proposal text for a deal from its quotation (not saved)")
    public Response<AiContentService.Proposal> proposal(@AuthenticationPrincipal User currentUser,
                                                        @Valid @RequestBody AiContentService.ProposalReq req) {
        return Response.ok(latency.time("proposal_writing", AiLatency.Kind.GENERATIVE, () -> content.proposal(currentUser, req)));
    }

    @GetMapping("/churn-risk")
    @PreAuthorize(CHURN_ROLES)
    @Operation(summary = "FR-AI-6.2: a customer's churn risk now (low/medium/high, 0–100) and the reasons")
    public Response<ChurnRiskService.ChurnRisk> churnRisk(@AuthenticationPrincipal User currentUser, @RequestParam Long customerId) {
        return Response.ok(latency.time("churn_risk", AiLatency.Kind.STATISTICAL, () -> churnRisk.assess(currentUser, customerId)));
    }

    @GetMapping("/churn-risks")
    @PreAuthorize(CHURN_ROLES)
    @Operation(summary = "FR-AI-6.2: customers I can see at risk of churning (level=medium|high), riskiest first")
    public Response<List<ChurnRiskService.ChurnRisk>> churnRisks(@AuthenticationPrincipal User currentUser,
                                                                @RequestParam(defaultValue = "medium") String level) {
        return Response.ok(latency.time("churn_risks", AiLatency.Kind.STATISTICAL, () -> churnRisk.atRisk(currentUser, level)));
    }

    @PostMapping("/sentiment")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-AI-6: sentiment and churn risk of a customer message (text or communicationId)")
    public Response<SentimentRes> sentiment(@AuthenticationPrincipal User currentUser, @Valid @RequestBody SentimentReq dto) {
        return Response.ok(latency.time("sentiment", AiLatency.Kind.GENERATIVE, () -> writing.sentiment(currentUser, dto)));
    }
}
