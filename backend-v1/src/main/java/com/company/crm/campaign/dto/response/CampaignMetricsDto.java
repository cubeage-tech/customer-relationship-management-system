package com.company.crm.campaign.dto.response;

import java.time.LocalDateTime;

/**
 * FR-7.4: a campaign's performance. Message figures come from tracked email/SMS sends; "converted"
 * recipients won a deal or approved a quotation within 30 days of receiving it. Rates are % of sent.
 * The lead figures are the leads attributed to the campaign (as before).
 */
public record CampaignMetricsDto(long recipients, long queued, long sent, long failed, long skipped,
                                 long opened, long clicked, long converted,
                                 double openRate, double clickRate, double conversionRate,
                                 long leadsGenerated, long convertedLeads) {

    /** One recipient, for the campaign's delivery list. */
    public record Recipient(Long customerId, String customerName, String address, String status, String errorMessage,
                            LocalDateTime sentAt, LocalDateTime openedAt, LocalDateTime clickedAt) {}
}
