package com.company.crm.task.scheduler;

import com.company.crm.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** FR-AI-7.3: every few minutes, remind assignees of follow-up tasks that have fallen due (once each). */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskReminderJob {

    private final TaskService taskService;

    @Scheduled(cron = "${app.tasks.reminder-cron:0 */10 * * * *}")
    public void remindDueTasks() {
        List<Long> due = taskService.findDue();
        for (Long taskId : due) {
            try {
                taskService.remind(taskId);
            } catch (RuntimeException e) {
                log.warn("Reminder for task {} failed: {}", taskId, e.getMessage());
            }
        }
        if (!due.isEmpty()) {
            log.info("Task reminders: processed {} task(s)", due.size());
        }
    }
}
