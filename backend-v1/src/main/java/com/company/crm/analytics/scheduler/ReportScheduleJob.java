package com.company.crm.analytics.scheduler;

import com.company.crm.analytics.service.ReportScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** REP-SCH: every few minutes, generate and email the scheduled reports that have fallen due. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportScheduleJob {

    private final ReportScheduleService scheduleService;

    @Scheduled(cron = "${app.reports.schedule-cron:0 */5 * * * *}")
    public void sendDueReports() {
        List<Long> due = scheduleService.findDue();
        for (Long id : due) {
            try {
                scheduleService.runDue(id);
            } catch (RuntimeException e) {
                log.warn("Scheduled report {} failed: {}", id, e.getMessage());
            }
        }
        if (!due.isEmpty()) {
            log.info("Scheduled reports: processed {} schedule(s)", due.size());
        }
    }
}
