package com.company.crm.campaign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** FR-5.x: tenant-wide campaign performance, for the marketing dashboard. */
@Getter
@AllArgsConstructor
public class CampaignSummaryDto {
    private long activeCampaigns;
    private long leadsGenerated;
    private long qualifiedLeads;
    private double conversionRate;
}
