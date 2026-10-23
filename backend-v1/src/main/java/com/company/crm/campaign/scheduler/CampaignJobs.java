package com.company.crm.campaign.scheduler;

import com.company.crm.campaign.service.CampaignDeliveryService;
import com.company.crm.campaign.service.CampaignScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * FR-7.1b/FR-7.1c: every minute, launch campaigns whose scheduled time has come, complete campaigns
 * past their end date, and send the next batch of queued campaign messages.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignJobs {

    private final CampaignScheduleService scheduleService;
    private final CampaignDeliveryService deliveryService;

    @Scheduled(cron = "${app.campaign.job-cron:0 * * * * *}")
    public void run() {
        each(scheduleService.dueToLaunch(), scheduleService::launchScheduled, "launch");
        each(scheduleService.dueToComplete(), scheduleService::complete, "complete");
        int sent = each(deliveryService.nextBatch(), deliveryService::send, "send");
        if (sent > 0) {
            log.info("Campaign sender: processed {} message(s)", sent);
        }
    }

    private static int each(List<Long> ids, Consumer<Long> action, String what) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (RuntimeException e) {
                log.warn("Campaign {} {} failed: {}", what, id, e.getMessage());
            }
        }
        return ids.size();
    }
}
