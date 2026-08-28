package com.company.crm.lead.mapper;

import com.company.crm.lead.dto.response.LeadResDto;
import com.company.crm.lead.entity.Lead;
import org.springframework.stereotype.Component;

@Component
public class LeadMapper {

    public LeadResDto toDto(Lead lead) {
        return new LeadResDto(
                lead.getId(),
                lead.getLeadName(),
                lead.getCompanyName(),
                lead.getContactEmail(),
                lead.getContactPhone(),
                lead.getIndustry().getDbValue(),
                lead.getSource().getDbValue(),
                lead.getStage().getDbValue(),
                lead.getFollowUpDate(),
                lead.getNotes(),
                lead.getOwner() != null ? lead.getOwner().getId() : null,
                lead.getOwner() != null ? lead.getOwner().getFullName() : null,
                lead.getConvertedCustomer() != null ? lead.getConvertedCustomer().getId() : null,
                lead.getCreatedAt()
        );
    }
}
