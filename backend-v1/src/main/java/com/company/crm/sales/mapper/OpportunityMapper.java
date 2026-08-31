package com.company.crm.sales.mapper;

import com.company.crm.sales.dto.response.OpportunityResDto;
import com.company.crm.sales.entity.Opportunity;
import org.springframework.stereotype.Component;

@Component
public class OpportunityMapper {

    public OpportunityResDto toDto(Opportunity opportunity) {
        return new OpportunityResDto(
                opportunity.getId(),
                opportunity.getCustomer().getId(),
                opportunity.getCustomer().getCompanyName(),
                opportunity.getLead() != null ? opportunity.getLead().getId() : null,
                opportunity.getProductService(),
                opportunity.getDealValue(),
                opportunity.getExpectedClosingDate(),
                opportunity.getStage().getDbValue(),
                opportunity.getLossReason(),
                opportunity.getOwner() != null ? opportunity.getOwner().getId() : null,
                opportunity.getOwner() != null ? opportunity.getOwner().getFullName() : null,
                opportunity.getStageChangedAt(),
                opportunity.getCreatedAt()
        );
    }
}
