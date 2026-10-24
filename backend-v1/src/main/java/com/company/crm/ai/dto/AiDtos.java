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

    public record Forecast(String period, LocalDate from, LocalDate to, BigDecimal wonSoFar, BigDecimal weightedPipeline,
                           BigDecimal expectedRevenue, BigDecimal previousPeriodRevenue, Double growthPercent,
                           BigDecimal target, Double targetCoveragePercent, double historicalWinRate,
                           List<ForecastDeal> deals, LocalDateTime generatedAt) {}

    // ==================== Chat (FR-AI-4) ====================

    @Getter
    @Setter
    public static class ChatReq {
        @NotBlank
        @Size(max = 1000)
        private String question;
    }

    /** What the model makes of the question — always one of the supported queries. */
    public record ChatPlan(
            @JsonPropertyDescription("One of: inactive_customers, leads, opportunities, tickets, top_customers, campaigns, unsupported")
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
            BigDecimal minValue) {}

    /** The model's wording of the result. */
    public record ChatWording(
            @JsonPropertyDescription("One or two sentences answering the question from the result given")
            String answer,
            @JsonPropertyDescription("One concrete next step for the user, e.g. send a reactivation campaign")
            String recommendedAction) {}

    public record ChatRow(Long id, String type, String label, String detail) {}

    public record ChatRes(String question, String intent, String answer, String recommendedAction, long matchCount,
                          List<ChatRow> rows) {}

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
        @NotBlank
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

    public record GeneratedMessage(String channel, String subject, String body) {}

    // ==================== Sentiment (FR-AI-6) ====================

    @Getter
    @Setter
    public static class SentimentReq {
        /** Text to analyse, or communicationId of a logged email/message. */
        @Size(max = 5000)
        private String text;
        private Long communicationId;
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

    public record SentimentRes(String sentiment, double score, String churnRisk, String reason, Long communicationId) {}
}
