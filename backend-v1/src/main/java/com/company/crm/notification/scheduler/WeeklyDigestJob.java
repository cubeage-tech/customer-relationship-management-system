package com.company.crm.notification.scheduler;

import com.company.crm.notification.service.WeeklyDigestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** NOT-1: every Monday morning, email each user their weekly digest. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyDigestJob {

    private final WeeklyDigestService digestService;

    @Value("${app.notifications.weekly-digest-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${app.notifications.weekly-digest-cron:0 0 8 * * MON}")
    public void sendDigests() {
        if (!enabled) {
            return;
        }
        int sent = 0;
        for (Long userId : digestService.recipients()) {
            try {
                if (digestService.send(userId)) {
                    sent++;
                }
            } catch (RuntimeException e) {
                log.warn("Weekly digest for user {} failed: {}", userId, e.getMessage());
            }
        }
        log.info("Weekly digest: sent to {} user(s)", sent);
    }
}
