package com.company.crm.subscription.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** The tenant's current subscription, its plan limits (null = unlimited) and current usage. */
@Getter
@AllArgsConstructor
public class SubscriptionResDto {
    /** Plan code, e.g. "business". */
    private String plan;
    private String planName;
    /** trial | active | past_due | cancelled | expired — evaluated against the clock. */
    private String status;
    /** monthly | annual; null while on a trial. */
    private String billingCycle;
    private LocalDateTime startedAt;
    /** End of the current period (trial end while on a trial). */
    private LocalDateTime expiresAt;
    private LocalDateTime cancelledAt;
    /** False once the subscription has lapsed — the tenant is read-only until it renews. */
    private boolean writeAccess;

    private Integer maxUsers;
    private Integer maxCustomers;
    private Integer maxCampaigns;

    private long usedUsers;
    private long usedCustomers;
    private long usedCampaigns;

    private Long planId;
    /** What the current billing cycle costs (the monthly price while on a trial); null if unpriced. */
    private BigDecimal price;
    private String currency;
    /** Whole days until expiresAt, rounded up; never negative. */
    private long daysRemaining;
    /** Length of the current period in days (for elapsed-vs-total progress). */
    private long totalDays;
    /** Always false today: renewal is a manual payment, there is no recurring charge. */
    private boolean autoRenew;
    private UsageResDto usage;
    private List<String> features;
    /** True when a higher active plan exists — allowed even when expired. */
    private boolean canUpgrade;
}
