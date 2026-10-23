package com.company.crm.analytics.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** FR-9.x report and dashboard shapes. Every report says the period it covers and when it was generated. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /** A labelled count and value (e.g. a stage, industry, month or product). */
    public record Row(String key, String label, long count, BigDecimal value) {}

    // FR-9.1
    public record Totals(long totalLeads, long convertedLeads, double conversionRate, BigDecimal revenue, long wonDeals) {}

    public record Headline(LocalDate from, LocalDate to, Totals current, Totals previous,
                           Double leadGrowthPercent, Double revenueGrowthPercent,
                           BigDecimal openPipelineValue, long openDeals, LocalDateTime generatedAt) {}

    // FR-9.2
    public record OwnerRow(Long ownerId, String ownerName, long openCount, BigDecimal openValue, long wonCount, BigDecimal wonValue,
                           long lostCount, BigDecimal lostValue, long totalCount, BigDecimal totalValue) {}

    public record SalesReport(LocalDate from, LocalDate to, long totalCount, BigDecimal totalValue, List<Row> byStage,
                              List<OwnerRow> byOwner, LocalDateTime generatedAt) {}

    // FR-9.3
    public record CustomerReport(LocalDate from, LocalDate to, long activeCustomers, long newCustomers, long contactedCustomers,
                                 long notContactedCustomers, List<Row> byIndustry, List<Row> activityByChannel, List<Row> segments,
                                 LocalDateTime generatedAt) {}

    // FR-9.4
    public record RevenueReport(LocalDate from, LocalDate to, BigDecimal totalRevenue, long wonDeals, List<Row> byMonth,
                                List<Row> byIndustry, List<Row> byProduct, List<Row> bySalesperson, LocalDateTime generatedAt) {}

    // FR-9.5
    public record PersonRow(Long userId, String name, Long teamId, String teamName, BigDecimal revenue, long wonDeals,
                            BigDecimal targetValue, BigDecimal targetActual, Double attainmentPercent,
                            long calls, long meetings, long quotationsSent, long visits, BigDecimal commission) {}

    public record TeamRow(Long teamId, String name, int members, BigDecimal revenue, long wonDeals, BigDecimal commission,
                          BigDecimal targetValue, BigDecimal targetActual, Double attainmentPercent) {}

    public record TeamPerformance(LocalDate from, LocalDate to, List<PersonRow> people, List<TeamRow> teams, LocalDateTime generatedAt) {}

    // REP-SVC
    public record ServiceRow(String key, String label, long created, long resolved, Double avgResolutionHours,
                             Double slaCompliancePercent, Double averageFeedback) {}

    public record ServiceReport(LocalDate from, LocalDate to, long created, long resolved, Double avgResolutionHours,
                                long resolvedWithinSla, Double slaCompliancePercent, long openBreached,
                                long feedbackCount, Double averageFeedback, List<ServiceRow> byPriority,
                                List<ServiceRow> byTechnician, LocalDateTime generatedAt) {}

    // REP-CMP
    public record CampaignRow(Long campaignId, String name, String channel, String status, LocalDate startDate, LocalDate endDate,
                              long sent, long opened, long clicked, long converted, double openRate, double clickRate,
                              double conversionRate, long leadsGenerated, long convertedLeads, BigDecimal actualCost,
                              BigDecimal costPerLead) {}

    public record CampaignReport(LocalDate from, LocalDate to, long sent, long opened, long clicked, long converted,
                                 long leadsGenerated, List<CampaignRow> campaigns, LocalDateTime generatedAt) {}

    // FR-9.6
    public record Option(Long id, String code, String label) {}

    public record FilterOptions(List<Option> teams, List<Option> users, List<Option> industries) {}

    // FR-9.1 configuration
    @Getter
    @Setter
    public static class Preferences {
        /** Tile keys in display order. */
        @NotNull
        @Size(min = 1, max = 12)
        private List<String> widgets;
        /** month | quarter | year | last30 */
        @NotNull
        private String period;
    }

    // REP-SCH
    @Getter
    @Setter
    public static class ScheduleReq {
        @NotBlank
        @Size(max = 150)
        private String name;
        /** sales | customers | revenue | team-performance | service | campaigns */
        @NotBlank
        private String reportType;
        /** pdf | xlsx */
        @NotBlank
        private String format;
        /** daily | weekly | monthly */
        @NotBlank
        private String frequency;
        @Min(0)
        @Max(23)
        private Integer hourOfDay;
        /** Emails of users in the same company; defaults to the owner. */
        @Size(max = 20)
        private List<String> recipients;
        private String industry;
        private Long teamId;
        private Long userId;
        private Boolean active;
    }

    public record ScheduleRes(Long id, String name, String reportType, String reportTitle, String format, String frequency,
                              int hourOfDay, List<String> recipients, String industry, Long teamId, Long userId, boolean active,
                              Long ownerId, String ownerName, LocalDateTime nextRunAt, LocalDateTime lastRunAt,
                              String lastStatus, String lastError) {}
}
