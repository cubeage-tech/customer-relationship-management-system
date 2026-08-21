package com.company.crm.plan.mapper;

import com.company.crm.plan.dto.response.PlanPriceResDto;
import com.company.crm.plan.entity.PlanPrice;
import org.springframework.stereotype.Component;

@Component
public class PlanPriceMapper {

    public PlanPriceResDto toDto(PlanPrice planPrice) {
        return new PlanPriceResDto(
                planPrice.getPlan().getDbValue(),
                planPrice.getMonthlyPrice(),
                planPrice.getAnnualPrice(),
                planPrice.getCurrency(),
                planPrice.getUpdatedAt()
        );
    }
}
