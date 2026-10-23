package com.company.crm.meeting.scheduler;

import com.company.crm.meeting.service.MeetingReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** FR-5.5b: every few minutes, send the reminders of meetings that are about to start. */
@Component
@RequiredArgsConstructor
@Slf4j
public class MeetingReminderJob {

    private final MeetingReminderService reminderService;

    @Scheduled(cron = "${app.meetings.reminder-cron:0 */5 * * * *}")
    public void sendDueReminders() {
        List<Long> due = reminderService.findDue();
        for (Long meetingId : due) {
            try {
                reminderService.remind(meetingId);
            } catch (RuntimeException e) {
                log.warn("Meeting reminder {} failed: {}", meetingId, e.getMessage());
            }
        }
        if (!due.isEmpty()) {
            log.info("Meeting reminders: processed {} meeting(s)", due.size());
        }
    }
}
