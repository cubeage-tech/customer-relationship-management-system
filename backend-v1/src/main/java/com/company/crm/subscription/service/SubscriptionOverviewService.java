package com.company.crm.subscription.service;

import com.company.crm.common.enums.SubscriptionStatus;
import com.company.crm.common.exception.InvalidPlanChangeException;
import com.company.crm.common.exception.SubscriptionNotFoundException;
import com.company.crm.payment.dto.request.PaymentOrderRequest;
import com.company.crm.payment.dto.response.PaymentOrderResponse;
import com.company.crm.payment.service.PaymentService;
import com.company.crm.plan.entity.Plan;
import com.company.crm.plan.entity.PlanPrice;
import com.company.crm.plan.repository.PlanPriceRepository;
import com.company.crm.plan.repository.PlanRepository;
import com.company.crm.subscription.dto.request.UpgradeReqDto;
import com.company.crm.subscription.dto.response.PlanOptionResDto;
import com.company.crm.subscription.dto.response.SubscriptionResDto;
import com.company.crm.subscription.entity.Subscription;
import com.company.crm.subscription.mapper.SubscriptionMapper;
import com.company.crm.subscription.mapper.SubscriptionMapper.Overview;
import com.company.crm.subscription.repository.SubscriptionRepository;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read model for the tenant admin's "My Plan" page, plus the upgrade entry point. Lifecycle
 * rules stay in SubscriptionService, limits in PlanLimitService, checkout in PaymentService.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionOverviewService {

    private final SubscriptionService subscriptionService;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanLimitService planLimitService;
    private final PlanRepository planRepository;
    private final PlanPriceRepository planPriceRepository;
    private final PaymentService paymentService;
    private final SubscriptionMapper subscriptionMapper;

    /** The tenant's subscription with price, time left, usage vs limits and features. Works when expired. */
    @Transactional(readOnly = true)
    public SubscriptionResDto describe(Tenant tenant) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenant.getId())
                .orElseThrow(() -> new SubscriptionNotFoundException("No subscription found for your company"));

        LocalDateTime now = LocalDateTime.now();
        SubscriptionStatus status = subscriptionService.effectiveStatus(subscription, now);
        Plan plan = subscription.getPlan();

        Overview overview = new Overview(
                status,
                subscriptionService.hasWriteAccess(status),
                planLimitService.usage(tenant.getId()),
                planPriceRepository.findByPlanCode(plan.getCode()).orElse(null),
                subscriptionService.daysRemaining(subscription, now),
                subscriptionService.totalDays(subscription),
                planRepository.existsByActiveTrueAndDisplayOrderGreaterThan(plan.getDisplayOrder()));

        return subscriptionMapper.toDto(subscription, overview);
    }

    /** All active plans, cheapest tier first, flagged as current / upgrade relative to the tenant's plan. */
    @Transactional(readOnly = true)
    public List<PlanOptionResDto> planOptions(Tenant tenant) {
        Plan current = subscriptionService.currentPlan(tenant);
        Map<Long, PlanPrice> pricesByPlanId = planPriceRepository.findAll().stream()
                .collect(Collectors.toMap(price -> price.getPlan().getId(), Function.identity()));

        return planRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(plan -> subscriptionMapper.toPlanOption(plan, pricesByPlanId.get(plan.getId()), current))
                .toList();
    }

    /**
     * Validates the move and opens a checkout with the payment provider. Nothing changes on the
     * subscription here — it switches plan only once the payment is verified (/api/payments/verify
     * or the provider webhook). Allowed even when the subscription has expired.
     */
    @Transactional
    public PaymentOrderResponse startUpgrade(User currentUser, UpgradeReqDto request) {
        Plan target = planRepository.findById(request.planId())
                .filter(Plan::getActive)
                .orElseThrow(() -> new InvalidPlanChangeException("That plan is not available"));

        assertIsUpgrade(subscriptionService.currentPlan(currentUser.getTenant()), target);

        return paymentService.createOrder(currentUser, new PaymentOrderRequest(target.getCode(), request.billingCycle()));
    }

    /** Only a strictly higher tier is an upgrade; same-plan renewals go through the regular checkout. */
    public static void assertIsUpgrade(Plan current, Plan target) {
        if (target.getDisplayOrder() <= current.getDisplayOrder()) {
            throw new InvalidPlanChangeException(
                    target.getName() + " is not an upgrade from your current " + current.getName() + " plan");
        }
    }
}
