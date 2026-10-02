package com.company.crm.subscription;

import com.company.crm.common.enums.BillingCycle;
import com.company.crm.common.enums.SubscriptionPlan;
import com.company.crm.common.enums.SubscriptionStatus;
import com.company.crm.common.exception.SubscriptionInactiveException;
import com.company.crm.payment.entity.Payment;
import com.company.crm.plan.entity.Plan;
import com.company.crm.plan.repository.PlanRepository;
import com.company.crm.subscription.entity.Subscription;
import com.company.crm.subscription.repository.SubscriptionRepository;
import com.company.crm.subscription.service.SubscriptionService;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private TenantRepository tenantRepository;

    @InjectMocks private SubscriptionService subscriptionService;

    private final LocalDateTime now = LocalDateTime.now();
    private Tenant tenant;
    private Plan business;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(subscriptionService, "trialPlanCode", "business");
        ReflectionTestUtils.setField(subscriptionService, "trialDays", 14);
        ReflectionTestUtils.setField(subscriptionService, "graceDays", 7);

        tenant = new Tenant();
        tenant.setId(7L);
        tenant.setPlan(SubscriptionPlan.STARTER);

        business = new Plan();
        business.setId(2L);
        business.setCode("business");
    }

    // ==================== effectiveStatus ====================

    @Test
    void effectiveStatus_followsTheLifecycle() {
        assertThat(status(SubscriptionStatus.TRIAL, now.plusDays(1))).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(status(SubscriptionStatus.TRIAL, now.minusMinutes(1))).isEqualTo(SubscriptionStatus.EXPIRED);

        assertThat(status(SubscriptionStatus.ACTIVE, now.plusDays(1))).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(status(SubscriptionStatus.ACTIVE, now.minusDays(3))).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(status(SubscriptionStatus.ACTIVE, now.minusDays(8))).isEqualTo(SubscriptionStatus.EXPIRED);

        assertThat(status(SubscriptionStatus.PAST_DUE, now.minusDays(8))).isEqualTo(SubscriptionStatus.EXPIRED);

        assertThat(status(SubscriptionStatus.CANCELLED, now.plusDays(10))).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(status(SubscriptionStatus.CANCELLED, now.minusMinutes(1))).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void onlyExpiredLosesWriteAccess() {
        assertThat(subscriptionService.hasWriteAccess(SubscriptionStatus.PAST_DUE)).isTrue();
        assertThat(subscriptionService.hasWriteAccess(SubscriptionStatus.CANCELLED)).isTrue();
        assertThat(subscriptionService.hasWriteAccess(SubscriptionStatus.EXPIRED)).isFalse();
    }

    @Test
    void assertWriteAccess_lapsedTenant_throws402() {
        when(subscriptionRepository.findByTenantId(7L))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.ACTIVE, now.minusDays(30))));

        assertThatThrownBy(() -> subscriptionService.assertWriteAccess(tenant))
                .isInstanceOf(SubscriptionInactiveException.class)
                .extracting("errorCode").isEqualTo("SUBSCRIPTION_INACTIVE");
    }

    @Test
    void assertWriteAccess_tenantWithoutSubscription_throws() {
        when(subscriptionRepository.findByTenantId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.assertWriteAccess(tenant))
                .isInstanceOf(SubscriptionInactiveException.class);
    }

    // ==================== trial ====================

    @Test
    void startTrial_createsTrialOnConfiguredPlanAndSyncsTenantPlan() {
        when(subscriptionRepository.findByTenantId(7L)).thenReturn(Optional.empty());
        when(planRepository.findByCode("business")).thenReturn(Optional.of(business));

        subscriptionService.startTrialIfAbsent(tenant);

        verify(subscriptionRepository).save(any(Subscription.class));
        assertThat(tenant.getPlan()).isEqualTo(SubscriptionPlan.BUSINESS);
    }

    @Test
    void startTrial_isIdempotent() {
        when(subscriptionRepository.findByTenantId(7L))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.TRIAL, now.plusDays(3))));

        subscriptionService.startTrialIfAbsent(tenant);

        verify(subscriptionRepository, never()).save(any());
    }

    // ==================== activate ====================

    @Test
    void activate_fromTrial_startsFreshPeriodFromNow() {
        Subscription trial = subscription(SubscriptionStatus.TRIAL, now.plusDays(10));
        when(subscriptionRepository.findByTenantId(7L)).thenReturn(Optional.of(trial));
        when(subscriptionRepository.save(trial)).thenReturn(trial);

        subscriptionService.activate(tenant, business, BillingCycle.MONTHLY, new Payment());

        assertThat(trial.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(trial.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(trial.getExpiresAt()).isCloseTo(now.plusMonths(1), within(5, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void activate_renewingSamePlanEarly_extendsFromCurrentExpiry() {
        LocalDateTime currentEnd = now.plusDays(10);
        Subscription active = subscription(SubscriptionStatus.ACTIVE, currentEnd);
        when(subscriptionRepository.findByTenantId(7L)).thenReturn(Optional.of(active));
        when(subscriptionRepository.save(active)).thenReturn(active);

        subscriptionService.activate(tenant, business, BillingCycle.ANNUAL, new Payment());

        assertThat(active.getExpiresAt()).isEqualTo(currentEnd.plusYears(1));
    }

    // ==================== days remaining ====================

    @Test
    void daysRemaining_roundsPartialDaysUp() {
        assertThat(subscriptionService.daysRemaining(subscription(SubscriptionStatus.ACTIVE, now.plusDays(10)), now))
                .isEqualTo(10);
        assertThat(subscriptionService.daysRemaining(subscription(SubscriptionStatus.ACTIVE, now.plusDays(10).plusHours(1)), now))
                .isEqualTo(11);
        assertThat(subscriptionService.daysRemaining(subscription(SubscriptionStatus.TRIAL, now.plusHours(23)), now))
                .isEqualTo(1);
    }

    @Test
    void daysRemaining_isNeverNegative() {
        assertThat(subscriptionService.daysRemaining(subscription(SubscriptionStatus.ACTIVE, now), now)).isZero();
        assertThat(subscriptionService.daysRemaining(subscription(SubscriptionStatus.EXPIRED, now.minusDays(40)), now)).isZero();
        // past_due is grace time, not paid time — nothing "remaining".
        assertThat(subscriptionService.daysRemaining(subscription(SubscriptionStatus.PAST_DUE, now.minusDays(2)), now)).isZero();
    }

    @Test
    void totalDays_isThePeriodLength_atLeastOne() {
        Subscription trial = subscription(SubscriptionStatus.TRIAL, now.plusDays(14));
        trial.setStartedAt(now);
        assertThat(subscriptionService.totalDays(trial)).isEqualTo(14);

        Subscription degenerate = subscription(SubscriptionStatus.ACTIVE, now);
        degenerate.setStartedAt(now);
        assertThat(subscriptionService.totalDays(degenerate)).isEqualTo(1);
    }

    private SubscriptionStatus status(SubscriptionStatus stored, LocalDateTime expiresAt) {
        return subscriptionService.effectiveStatus(subscription(stored, expiresAt), now);
    }

    private Subscription subscription(SubscriptionStatus status, LocalDateTime expiresAt) {
        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setTenant(tenant);
        subscription.setPlan(business);
        subscription.setStatus(status);
        subscription.setStartedAt(now.minusDays(20));
        subscription.setExpiresAt(expiresAt);
        return subscription;
    }
}
