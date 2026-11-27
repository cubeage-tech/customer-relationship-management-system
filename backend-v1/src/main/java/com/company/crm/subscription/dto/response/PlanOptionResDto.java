package com.company.crm.subscription.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** One plan the tenant can compare against, flagged relative to its current plan. */
public record PlanOptionResDto(
        Long id,
        String code,
        String name,
        String description,
        /** Price of one month on monthly billing. */
        BigDecimal monthlyPrice,
        /** Price of one year on annual billing (12 × the discounted monthly rate). */
        BigDecimal annualPrice,
        String currency,
        Integer maxUsers,
        Integer maxCustomers,
        Integer maxCampaigns,
        List<String> features,
        boolean current,
        /** Higher tier than the current plan — the only plans /upgrade accepts. */
        boolean upgrade
) {}
