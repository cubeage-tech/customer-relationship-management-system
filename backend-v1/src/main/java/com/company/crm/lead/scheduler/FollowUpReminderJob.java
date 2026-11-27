package com.company.crm.lead.scheduler;

import com.company.crm.lead.service.FollowUpReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** FR-2.5: every few minutes, remind owners of lead follow-ups that have fallen due. */
@Component
@RequiredArgsConstructor
@Slf4j
public class FollowUpReminderJob {

    private final FollowUpReminderService reminderService;

    @Scheduled(cron = "${app.leads.follow-up-reminder-cron:0 */10 * * * *}")
    public void remindDueFollowUps() {
        List<Long> due = reminderService.findDue();
        for (Long leadId : due) {
            try {
                reminderService.remind(leadId);
            } catch (RuntimeException e) {
                log.warn("Follow-up reminder for lead {} failed: {}", leadId, e.getMessage());
            }
        }
        if (!due.isEmpty()) {
            log.info("Follow-up reminders: processed {} lead(s)", due.size());
        }
    }
}
