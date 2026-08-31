package com.company.crm.plan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PlanPriceResDto {
    private String plan;
    private BigDecimal monthlyPrice;
    private BigDecimal annualPrice;
    private String currency;
    private LocalDateTime updatedAt;
}
