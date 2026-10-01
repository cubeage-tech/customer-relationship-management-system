package com.company.crm.subscription.service;

import com.company.crm.common.enums.BillingCycle;
import com.company.crm.common.enums.SubscriptionPlan;
import com.company.crm.common.enums.SubscriptionStatus;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.exception.SubscriptionInactiveException;
import com.company.crm.payment.entity.Payment;
import com.company.crm.plan.entity.Plan;
import com.company.crm.plan.repository.PlanRepository;
import com.company.crm.subscription.entity.Subscription;
import com.company.crm.subscription.repository.SubscriptionRepository;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Owns the subscription lifecycle:
 * <pre>
 *   (email verified) → TRIAL ──expires──────────────────────────────→ EXPIRED
 *   (payment)        → ACTIVE ──expires→ PAST_DUE ──grace ends──────→ EXPIRED
 *   ACTIVE ──cancel→ CANCELLED (keeps access until expires_at) ─────→ EXPIRED
 *   any state ──payment→ ACTIVE
 * </pre>
 * Status is always evaluated against the clock ({@link #effectiveStatus}), so access checks
 * are correct even between runs of the scheduled job that persists the transitions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    /** Statuses whose stored value can go stale as time passes. */
    private static final Set<SubscriptionStatus> TIME_BOUND = EnumSet.of(
            SubscriptionStatus.TRIAL,
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.CANCELLED
    );

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final TenantRepository tenantRepository;

    @Value("${app.subscription.trial-plan:business}")
    private String trialPlanCode;

    @Value("${app.subscription.trial-days:14}")
    private int trialDays;

    @Value("${app.subscription.grace-days:7}")
    private int graceDays;

    // ==================== Lifecycle ====================

    /** Starts the free trial for a newly verified tenant. No-op if the tenant already has a subscription. */
    @Transactional
    public void startTrialIfAbsent(Tenant tenant) {
        if (subscriptionRepository.findByTenantId(tenant.getId()).isPresent()) {
            return;
        }

        Plan trialPlan = planRepository.findByCode(trialPlanCode)
                .orElseThrow(() -> new IllegalStateException("Trial plan '" + trialPlanCode + "' is not seeded"));

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = new Subscription();
        subscription.setTenant(tenant);
        subscription.setPlan(trialPlan);
        subscription.setStatus(SubscriptionStatus.TRIAL);
        subscription.setStartedAt(now);
        subscription.setExpiresAt(now.plusDays(trialDays));
        subscriptionRepository.save(subscription);

        syncTenantPlan(tenant, trialPlan);
        log.info("Started {}-day {} trial for tenant {}", trialDays, trialPlanCode, tenant.getId());
    }

    /**
     * Activates or renews the tenant's subscription after a successful payment. Paying for
     * the same plan while the current paid period is still running extends it; anything
     * else (trial, lapsed, plan change) starts a fresh period from now.
     */
    @Transactional
    public Subscription activate(Tenant tenant, Plan plan, BillingCycle billingCycle, Payment payment) {
        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = subscriptionRepository.findByTenantId(tenant.getId())
                .orElseGet(() -> {
                    Subscription fresh = new Subscription();
                    fresh.setTenant(tenant);
                    return fresh;
                });

        boolean extendsCurrentPeriod = subscription.getId() != null
                && subscription.getPlan().getId().equals(plan.getId())
                && subscription.getStatus() != SubscriptionStatus.TRIAL
                && subscription.getExpiresAt().isAfter(now);

        LocalDateTime periodStart = extendsCurrentPeriod ? subscription.getExpiresAt() : now;
        if (!extendsCurrentPeriod) {
            subscription.setStartedAt(now);
        }

        subscription.setPlan(plan);
        subscription.setBillingCycle(billingCycle);
        subscription.setPayment(payment);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCancelledAt(null);
        subscription.setExpiresAt(billingCycle == BillingCycle.ANNUAL
                ? periodStart.plusYears(1)
                : periodStart.plusMonths(1));

        Subscription saved = subscriptionRepository.save(subscription);
        syncTenantPlan(tenant, plan);
        return saved;
    }

    /** Cancels at period end: the tenant keeps full access until expires_at, then it expires. */
    @Transactional
    public Subscription cancel(Tenant tenant) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenant.getId())
                .orElseThrow(() -> ApiException.notFound("No subscription found"));

        SubscriptionStatus status = effectiveStatus(subscription, LocalDateTime.now());
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.PAST_DUE) {
            throw ApiException.badRequest("Only a paid, active subscription can be cancelled (current status: "
                    + status.getDbValue() + ")");
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());
        return subscriptionRepository.save(subscription);
    }

    /** Persists time-based transitions (TRIAL/ACTIVE → PAST_DUE → EXPIRED). Run periodically. */
    @Transactional
    public int refreshExpiredStatuses() {
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> stale = subscriptionRepository.findByStatusInAndExpiresAtBefore(TIME_BOUND, now);

        int changed = 0;
        for (Subscription subscription : stale) {
            SubscriptionStatus next = effectiveStatus(subscription, now);
            if (next != subscription.getStatus()) {
                subscription.setStatus(next);
                changed++;
            }
        }
        subscriptionRepository.saveAll(stale);
        return changed;
    }

    // ==================== Queries / access checks ====================

    @Transactional(readOnly = true)
    public Optional<Subscription> findForTenant(Long tenantId) {
        return subscriptionRepository.findByTenantId(tenantId);
    }

    /** The tenant's status right now. A tenant with no subscription at all is treated as EXPIRED. */
    @Transactional(readOnly = true)
    public SubscriptionStatus currentStatus(Tenant tenant) {
        return subscriptionRepository.findByTenantId(tenant.getId())
                .map(subscription -> effectiveStatus(subscription, LocalDateTime.now()))
                .orElse(SubscriptionStatus.EXPIRED);
    }

    /** The plan whose limits apply to the tenant right now. */
    @Transactional(readOnly = true)
    public Plan currentPlan(Tenant tenant) {
        return subscriptionRepository.findByTenantId(tenant.getId())
                .map(Subscription::getPlan)
                .or(() -> planRepository.findByCode(tenant.getPlan().getDbValue()))
                .orElseThrow(() -> new IllegalStateException("No plan found for tenant " + tenant.getId()));
    }

    /** Lapsed tenants are read-only — throws on any write attempt. */
    public void assertWriteAccess(Tenant tenant) {
        if (!hasWriteAccess(currentStatus(tenant))) {
            throw new SubscriptionInactiveException(
                    "Your subscription has expired. Your data is read-only until you renew your plan.");
        }
    }

    public boolean hasWriteAccess(SubscriptionStatus status) {
        return status != SubscriptionStatus.EXPIRED;
    }

    /** What the subscription's status is at {@code now}, regardless of when it was last persisted. */
    public SubscriptionStatus effectiveStatus(Subscription subscription, LocalDateTime now) {
        LocalDateTime expiresAt = subscription.getExpiresAt();
        boolean inPeriod = expiresAt.isAfter(now);
        boolean inGrace = expiresAt.plusDays(graceDays).isAfter(now);

        return switch (subscription.getStatus()) {
            case TRIAL -> inPeriod ? SubscriptionStatus.TRIAL : SubscriptionStatus.EXPIRED;
            case ACTIVE -> inPeriod ? SubscriptionStatus.ACTIVE
                    : inGrace ? SubscriptionStatus.PAST_DUE : SubscriptionStatus.EXPIRED;
            case PAST_DUE -> inGrace ? SubscriptionStatus.PAST_DUE : SubscriptionStatus.EXPIRED;
            case CANCELLED -> inPeriod ? SubscriptionStatus.CANCELLED : SubscriptionStatus.EXPIRED;
            case EXPIRED -> SubscriptionStatus.EXPIRED;
        };
    }

    /**
     * Whole days left in the current period, rounded up (23 hours left = 1 day), never negative.
     * Once the period has ended this is 0 — even while past_due, whose grace period is not paid time.
     */
    public long daysRemaining(Subscription subscription, LocalDateTime now) {
        return ceilDays(Duration.between(now, subscription.getExpiresAt()));
    }

    /** Length of the current period in days (at least 1), for elapsed-vs-total progress. */
    public long totalDays(Subscription subscription) {
        return Math.max(1, ceilDays(Duration.between(subscription.getStartedAt(), subscription.getExpiresAt())));
    }

    private static long ceilDays(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        long secondsPerDay = Duration.ofDays(1).getSeconds();
        return (seconds + secondsPerDay - 1) / secondsPerDay;
    }

    /** tenants.plan is a denormalized cache of the current plan, read by the super-admin dashboards. */
    private void syncTenantPlan(Tenant tenant, Plan plan) {
        tenant.setPlan(SubscriptionPlan.fromDbValue(plan.getCode()));
        tenantRepository.save(tenant);
    }
}
