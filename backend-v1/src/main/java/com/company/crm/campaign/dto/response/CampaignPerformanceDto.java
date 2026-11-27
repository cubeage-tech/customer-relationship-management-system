package com.company.crm.campaign.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * API-G08: one campaign's performance — delivery and engagement (sent, opened, clicked, converted
 * and their rates), the leads it generated and how many converted, the revenue of the deals won
 * from those leads, cost per lead / conversion and return on spend, and day-by-day activity.
 */
public record CampaignPerformanceDto(Long campaignId, String name, String channel, String status, LocalDate startDate, LocalDate endDate,
                                     CampaignMetricsDto metrics, BigDecimal budget, BigDecimal actualCost,
                                     BigDecimal costPerLead, BigDecimal costPerConversion,
                                     long wonDeals, BigDecimal attributedRevenue, Double returnOnSpendPercent,
                                     List<Day> daily) {

    public record Day(LocalDate date, long sent, long opened, long clicked) {}
}
