package com.company.crm.campaign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CampaignResDto {
    private Long id;
    private String name;
    private String description;
    private String channel;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal budget;
    private BigDecimal actualCost;
    private Long ownerId;
    private String ownerName;
    // FR-5.3: leads this campaign has generated, and how many converted to a customer.
    private long leadsGenerated;
    private long convertedLeads;
    private double conversionRate;
    private LocalDateTime createdAt;
}
