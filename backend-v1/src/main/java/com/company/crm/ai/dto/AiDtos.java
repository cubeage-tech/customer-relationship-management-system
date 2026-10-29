package com.company.crm.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** API-G11 request/response shapes, and the structured answers asked of the model. */
public final class AiDtos {

    private AiDtos() {
    }

    // ==================== Lead score (FR-AI-1) ====================

    /** One input to the score: its 0–1 value, its weight in the score, and what it is based on. */
    public record Factor(String name, double value, double weight, String detail) {}

    public record LeadScore(Long leadId, String leadName, String companyName, String stage, int score, String label,
                            List<Factor> factors) {}

    // ==================== Forecast (FR-AI-2) ====================

    public record ForecastDeal(Long opportunityId, String customerName, BigDecimal dealValue, String stage,
                               LocalDate expectedClosingDate, double probability, BigDecimal weightedValue) {}

    /** FR-AI-2.1: revenue won in one earlier period of the same length. */
    public record PeriodRevenue(LocalDate from, LocalDate to, BigDecimal won) {}

    /**
     * {@code newBusiness}: revenue expected from deals not in the pipeline yet (the recent run rate of
     * deals created and won within one period). {@code low}/{@code high}: the likely range (~80%).
     * FR-AI-2.3: {@code variancePercent} = forecast vs target, {@code gapToTarget} = what is still missing.
     */
    public record Forecast(String period, LocalDate from, LocalDate to, BigDecimal wonSoFar, BigDecimal weightedPipeline,
                           BigDecimal expectedRevenue, BigDecimal previousPeriodRevenue, Double growthPercent,
                           BigDecimal target, Double targetCoveragePercent, double historicalWinRate,
                           List<ForecastDeal> deals, LocalDateTime generatedAt,
                           BigDecimal newBusiness, BigDecimal low, BigDecimal high, Double variancePercent, BigDecimal gapToTarget,
                           List<PeriodRevenue> history) {}

    // ==================== Deal close probability (FR-AI-2.2) ====================

    /** One reason the chance moved: {@code effect} multiplies the stage-based chance (1.15 = 15% more likely). */
    public record DealFactor(String name, double effect, String detail) {}

    public record DealProbability(Long opportunityId, String customerName, String ownerName, String stage, BigDecimal dealValue,
                                  LocalDate expectedClosingDate, double probability, int percent, String label, BigDecimal expectedValue,
                                  List<DealFactor> factors) {}

    // ==================== Product recommendations (FR-AI-3.1) ====================

    /** {@code kind}: bought_together | next_purchase | reorder | popular_in_industry. */
    public record ProductRec(String productName, BigDecimal unitPrice, String kind, double score, String reason) {}

    public record ProductRecs(Long customerId, List<String> purchased, List<ProductRec> recommendations) {}

    // ==================== Best time to contact (FR-AI-3.2) ====================

    /** {@code share}: the slot's share of the customer's likely-response pattern, in %. */
    public record ContactWindow(String day, String from, String to, String label, double share) {}

    public record ContactTime(String recordType, Long recordId, List<ContactWindow> windows, String basis, long interactions,
                              List<String> notes) {}

    // ==================== Sales approach / talking points (FR-AI-3.3) ====================

    @Getter
    @Setter
    public static class TalkingPointsReq {
        /** One of customerId / leadId / opportunityId. */
        private Long customerId;
        private Long leadId;
        private Long opportunityId;
    }

    public record Objection(
            @JsonPropertyDescription("An objection the customer is likely to raise") String objection,
            @JsonPropertyDescription("A short, honest way to answer it") String response) {}

    /** What the model returns. */
    public record SalesApproachPlan(
            @JsonPropertyDescription("Two or three sentences: how to approach this customer now, and why") String approach,
            @JsonPropertyDescription("Three to five short talking points grounded in the facts given") List<String> talkingPoints,
            @JsonPropertyDescription("Two or three open questions to ask") List<String> questions,
            @JsonPropertyDescription("Up to three likely objections, each with a response") List<Objection> objections) {}

    /** {@code basedOn}: the CRM facts the suggestion was made from, so the salesperson can judge it. */
    public record SalesApproach(String recordType, Long recordId, String approach, List<String> talkingPoints, List<String> questions,
                                List<Objection> objections, List<String> basedOn) {}

    // ==================== Chat (FR-AI-4) ====================

    @Getter
    @Setter
    public static class ChatReq {
        @NotBlank
        @Size(max = 1000)
        private String question;

        /** FR-AI-4.1: the conversation's earlier questions (latest first is fine), so follow-ups have context. */
        @Size(max = 5)
        private List<@Size(max = 1000) String> history;
    }

    /** What the model makes of the question — always one of the supported queries. */
    public record ChatPlan(
            @JsonPropertyDescription("One of: inactive_customers, leads, hot_leads, opportunities, likely_deals, forecast, follow_ups, meetings, tickets, top_customers, campaigns, unsupported")
            String intent,
            @JsonPropertyDescription("Look-back window in days when the question mentions one (e.g. 6 months = 180), else null")
            Integer days,
            @JsonPropertyDescription("Lead or opportunity stage code if asked for, e.g. new_lead, negotiation, won, else null")
            String stage,
            @JsonPropertyDescription("Lead source code if asked for, e.g. website, referral, exhibition, else null")
            String source,
            @JsonPropertyDescription("Industry code if asked for, e.g. trading, software, manufacturing, else null")
            String industry,
            @JsonPropertyDescription("Ticket status if asked for: open, assigned, in_progress, resolved, closed, else null")
            String status,
            @JsonPropertyDescription("Ticket priority if asked for: low, medium, high, critical, else null")
            String priority,
            @JsonPropertyDescription("Minimum deal value if asked for, else null")
            BigDecimal minValue,
            @JsonPropertyDescription("Forecast period if asked: this_month, next_month, this_quarter or next_quarter, else null")
            String period,
            @JsonPropertyDescription("AI score label for hot (High), warm (Medium) or cold (Low) leads, else null")
            String scoreLabel) {}

    /** The model's wording of the result. */
    public record ChatWording(
            @JsonPropertyDescription("One or two sentences answering the question from the result given")
            String answer,
            @JsonPropertyDescription("One concrete next step for the user, e.g. send a reactivation campaign")
            String recommendedAction) {}

    public record ChatRow(Long id, String type, String label, String detail) {}

    /** FR-AI-4.2: {@code rows} the matching records (structured), {@code answer} the summary; {@code viewAllLink} the full list. */
    public record ChatRes(String question, String intent, String answer, String recommendedAction, long matchCount,
                          List<ChatRow> rows, String viewAllLink) {}

    // ==================== Email & message generation (FR-AI-5) ====================

    @Getter
    @Setter
    public static class GenerateReq {
        /** One of customerId / leadId / opportunityId gives the context. */
        private Long customerId;
        private Long leadId;
        private Long opportunityId;
        /** email | whatsapp | sms */
        private String channel = "email";
        /** FR-AI-5.3: message (default) | follow_up — a follow-up to the latest contact; its purpose may be left out. */
        private String kind = "message";
        @Size(max = 500)
        private String purpose;
        /** friendly | formal | persuasive */
        private String tone = "friendly";
        @Size(max = 1000)
        private String notes;
    }

    public record GeneratedText(
            @JsonPropertyDescription("Email subject line; empty for WhatsApp and SMS")
            String subject,
            @JsonPropertyDescription("The message body, ready to send, signed off without a placeholder name")
            String body) {}

    /** {@code bestTimeToSend}: FR-AI-3.2 — when the recipient is most likely to respond (null if unknown). */
    /** {@code draftId}: FR-AI-5.2 — send it with the message (and the review confirmation) when sending this draft. */
    public record GeneratedMessage(String channel, String subject, String body, String bestTimeToSend, Long draftId) {}

    // ==================== Sentiment (FR-AI-6) ====================

    @Getter
    @Setter
    public static class SentimentReq {
        /** Text to analyse, communicationId of a logged email/message, or ticketId for its feedback (FR-AI-6.1: those two are stored). */
        @Size(max = 5000)
        private String text;
        private Long communicationId;
        private Long ticketId;
    }

    /** FR-AI-6.1: the stored sentiment of a message or of ticket feedback (null when not classified). */
    public record SentimentTag(String sentiment, BigDecimal score, String churnRisk, String reason, LocalDateTime at) {
        public static SentimentTag of(String sentiment, BigDecimal score, String churnRisk, String reason, LocalDateTime at) {
            return sentiment == null ? null : new SentimentTag(sentiment, score, churnRisk, reason, at);
        }
    }

    public record SentimentJudgement(
            @JsonPropertyDescription("positive, neutral or negative")
            String sentiment,
            @JsonPropertyDescription("From -1 (very negative) to 1 (very positive)")
            double score,
            @JsonPropertyDescription("Risk of losing this customer: low, medium or high")
            String churnRisk,
            @JsonPropertyDescription("One short sentence on what in the text drove the judgement")
            String reason) {}

    public record SentimentRes(String sentiment, double score, String churnRisk, String reason, Long communicationId, Long ticketId) {}
}
