package com.company.crm.subscription.scheduler;

import com.company.crm.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Persists time-based subscription transitions so reports and the super-admin views show
 * the real status. Access checks don't depend on this job — they evaluate status live.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryJob {

    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "${app.subscription.status-refresh-cron:0 0 * * * *}")
    public void refreshStatuses() {
        int changed = subscriptionService.refreshExpiredStatuses();
        if (changed > 0) {
            log.info("Subscription status refresh: {} subscription(s) moved to past_due/expired", changed);
        }
    }
}
