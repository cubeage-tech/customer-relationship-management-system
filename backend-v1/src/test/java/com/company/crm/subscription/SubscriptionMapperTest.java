package com.company.crm.subscription;

import com.company.crm.common.enums.BillingCycle;
import com.company.crm.common.enums.SubscriptionStatus;
import com.company.crm.plan.entity.Plan;
import com.company.crm.plan.entity.PlanPrice;
import com.company.crm.subscription.dto.response.PlanOptionResDto;
import com.company.crm.subscription.dto.response.SubscriptionResDto;
import com.company.crm.subscription.dto.response.UsageResDto;
import com.company.crm.subscription.entity.Subscription;
import com.company.crm.subscription.mapper.SubscriptionMapper;
import com.company.crm.subscription.mapper.SubscriptionMapper.Overview;
import com.company.crm.subscription.service.PlanLimitService.Usage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionMapperTest {

    private final SubscriptionMapper mapper = new SubscriptionMapper();

    @Test
    void usage_pairsEachLimitWithItsCount_nullMeansUnlimited() {
        Plan plan = plan(1L, "starter", 1, 5, 500, null);

        UsageResDto usage = mapper.toUsage(plan, new Usage(3, 499, 7));

        assertThat(usage.users()).isEqualTo(new UsageResDto.Item(5, 3));
        assertThat(usage.customers()).isEqualTo(new UsageResDto.Item(500, 499));
        assertThat(usage.campaigns()).isEqualTo(new UsageResDto.Item(null, 7));
    }

    @Test
    void toDto_trialShowsMonthlyPrice_annualShowsTwelveTimesTheAnnualRate() {
        Plan business = plan(2L, "business", 2, 25, 5000, 50);
        business.setFeatures(List.of("Customer 360"));
        PlanPrice price = price(business, "3900.00", "3100.00");

        Subscription trial = subscription(business, null);
        SubscriptionResDto trialDto = mapper.toDto(trial, overview(SubscriptionStatus.TRIAL, price));
        assertThat(trialDto.getPrice()).isEqualByComparingTo("3900.00");
        assertThat(trialDto.getBillingCycle()).isNull();
        assertThat(trialDto.getStatus()).isEqualTo("trial");

        Subscription annual = subscription(business, BillingCycle.ANNUAL);
        SubscriptionResDto annualDto = mapper.toDto(annual, overview(SubscriptionStatus.ACTIVE, price));
        assertThat(annualDto.getPrice()).isEqualByComparingTo("37200.00");
        assertThat(annualDto.getCurrency()).isEqualTo("INR");
        assertThat(annualDto.getFeatures()).containsExactly("Customer 360");
        assertThat(annualDto.getUsage().users()).isEqualTo(new UsageResDto.Item(25, 2));
        assertThat(annualDto.getDaysRemaining()).isEqualTo(9);
        assertThat(annualDto.isAutoRenew()).isFalse();
        assertThat(annualDto.isCanUpgrade()).isTrue();
    }

    @Test
    void planOption_flagsCurrentAndHigherTiers() {
        Plan starter = plan(1L, "starter", 1, 5, 500, 5);
        Plan business = plan(2L, "business", 2, 25, 5000, 50);
        Plan enterprise = plan(3L, "enterprise", 3, null, null, null);

        PlanOptionResDto current = mapper.toPlanOption(business, price(business, "3900.00", "3100.00"), business);
        PlanOptionResDto lower = mapper.toPlanOption(starter, price(starter, "1500.00", "1200.00"), business);
        PlanOptionResDto higher = mapper.toPlanOption(enterprise, null, business);

        assertThat(current.current()).isTrue();
        assertThat(current.upgrade()).isFalse();
        assertThat(current.annualPrice()).isEqualByComparingTo("37200.00");
        assertThat(lower.current()).isFalse();
        assertThat(lower.upgrade()).isFalse();
        assertThat(higher.upgrade()).isTrue();
        assertThat(higher.monthlyPrice()).isNull(); // unpriced plan is still listed
    }

    private Overview overview(SubscriptionStatus status, PlanPrice price) {
        return new Overview(status, true, new Usage(2, 10, 1), price, 9, 30, true);
    }

    private Subscription subscription(Plan plan, BillingCycle cycle) {
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setBillingCycle(cycle);
        subscription.setStartedAt(LocalDateTime.now().minusDays(21));
        subscription.setExpiresAt(LocalDateTime.now().plusDays(9));
        return subscription;
    }

    private Plan plan(Long id, String code, int order, Integer users, Integer customers, Integer campaigns) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setCode(code);
        plan.setName(code.substring(0, 1).toUpperCase() + code.substring(1));
        plan.setDisplayOrder(order);
        plan.setMaxUsers(users);
        plan.setMaxCustomers(customers);
        plan.setMaxCampaigns(campaigns);
        return plan;
    }

    private PlanPrice price(Plan plan, String monthly, String annualRate) {
        PlanPrice price = new PlanPrice();
        price.setPlan(plan);
        price.setMonthlyPrice(new BigDecimal(monthly));
        price.setAnnualPrice(new BigDecimal(annualRate));
        price.setCurrency("INR");
        return price;
    }
}
