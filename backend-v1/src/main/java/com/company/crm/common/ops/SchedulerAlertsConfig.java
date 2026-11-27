package com.company.crm.common.ops;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** ERR-5: a scheduled job that fails outright is a critical error too (logged and alerted). */
@Configuration
@Slf4j
public class SchedulerAlertsConfig {

    @Bean
    ThreadPoolTaskSchedulerCustomizer opsAlertingScheduler(OpsAlertService opsAlerts) {
        return scheduler -> scheduler.setErrorHandler(error -> {
            String incident = OpsAlertService.newIncidentId();
            log.error("Scheduled job failed [incident {}]", incident, error);
            opsAlerts.critical(incident, "scheduled job", error);
        });
    }
}
