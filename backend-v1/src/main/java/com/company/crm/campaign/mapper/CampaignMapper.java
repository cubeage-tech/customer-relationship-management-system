package com.company.crm.campaign.mapper;

import com.company.crm.campaign.dto.response.CampaignResDto;
import com.company.crm.campaign.entity.Campaign;
import org.springframework.stereotype.Component;

@Component
public class CampaignMapper {

    public CampaignResDto toDto(Campaign campaign, long leadsGenerated, long convertedLeads) {
        double conversionRate = leadsGenerated == 0 ? 0.0 : (convertedLeads * 100.0) / leadsGenerated;
        return new CampaignResDto(
                campaign.getId(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getChannel().getDbValue(),
                campaign.getStatus().getDbValue(),
                campaign.getStartDate(),
                campaign.getEndDate(),
                campaign.getBudget(),
                campaign.getActualCost(),
                campaign.getOwner() != null ? campaign.getOwner().getId() : null,
                campaign.getOwner() != null ? campaign.getOwner().getFullName() : null,
                leadsGenerated,
                convertedLeads,
                conversionRate,
                campaign.getCreatedAt()
        );
    }
}
