package com.company.crm.subscription.mapper;

import com.company.crm.common.enums.BillingCycle;
import com.company.crm.common.enums.SubscriptionStatus;
import com.company.crm.plan.entity.Plan;
import com.company.crm.plan.entity.PlanPrice;
import com.company.crm.subscription.dto.response.PlanOptionResDto;
import com.company.crm.subscription.dto.response.SubscriptionResDto;
import com.company.crm.subscription.dto.response.UsageResDto;
import com.company.crm.subscription.entity.Subscription;
import com.company.crm.subscription.service.PlanLimitService.Usage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class SubscriptionMapper {

    /** Everything the "My Plan" page shows, computed by SubscriptionOverviewService. */
    public record Overview(
            SubscriptionStatus effectiveStatus,
            boolean writeAccess,
            Usage usage,
            PlanPrice price,
            long daysRemaining,
            long totalDays,
            boolean canUpgrade
    ) {}

    public SubscriptionResDto toDto(Subscription subscription, Overview overview) {
        Plan plan = subscription.getPlan();
        Usage usage = overview.usage();
        PlanPrice price = overview.price();
        return new SubscriptionResDto(
                plan.getCode(),
                plan.getName(),
                overview.effectiveStatus().getDbValue(),
                subscription.getBillingCycle() != null ? subscription.getBillingCycle().getDbValue() : null,
                subscription.getStartedAt(),
                subscription.getExpiresAt(),
                subscription.getCancelledAt(),
                overview.writeAccess(),
                plan.getMaxUsers(),
                plan.getMaxCustomers(),
                plan.getMaxCampaigns(),
                usage.users(),
                usage.customers(),
                usage.campaigns(),
                plan.getId(),
                price == null ? null
                        : subscription.getBillingCycle() != null
                                ? price.amountFor(subscription.getBillingCycle())
                                : price.getMonthlyPrice(),
                price == null ? null : price.getCurrency(),
                overview.daysRemaining(),
                overview.totalDays(),
                false,
                toUsage(plan, usage),
                List.copyOf(plan.getFeatures()),
                overview.canUpgrade()
        );
    }

    /** Pairs each plan limit (null = unlimited) with the tenant's current count. */
    public UsageResDto toUsage(Plan plan, Usage usage) {
        return new UsageResDto(
                new UsageResDto.Item(plan.getMaxUsers(), usage.users()),
                new UsageResDto.Item(plan.getMaxCustomers(), usage.customers()),
                new UsageResDto.Item(plan.getMaxCampaigns(), usage.campaigns()));
    }

    public PlanOptionResDto toPlanOption(Plan plan, PlanPrice price, Plan currentPlan) {
        BigDecimal monthly = price == null ? null : price.getMonthlyPrice();
        BigDecimal annual = price == null ? null : price.amountFor(BillingCycle.ANNUAL);
        return new PlanOptionResDto(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                monthly,
                annual,
                price == null ? null : price.getCurrency(),
                plan.getMaxUsers(),
                plan.getMaxCustomers(),
                plan.getMaxCampaigns(),
                List.copyOf(plan.getFeatures()),
                plan.getId().equals(currentPlan.getId()),
                plan.getDisplayOrder() > currentPlan.getDisplayOrder()
        );
    }
}
