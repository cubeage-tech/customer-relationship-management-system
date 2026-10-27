package com.company.crm.communication.scheduler;

import com.company.crm.common.mail.MailOutboxService;
import com.company.crm.communication.service.CommunicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * NFR-7.4a: every minute, retry outbound messages that failed for a temporary reason — emails
 * held in the mail outbox, and SMS/WhatsApp messages waiting for a resend. Each is retried on
 * its own, so one failure never blocks the rest.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboundRetryJob {

    private final MailOutboxService mailOutbox;
    private final CommunicationService communicationService;

    @Scheduled(cron = "${app.outbound.retry-cron:30 * * * * *}")
    public void retryDue() {
        List<Long> emails = mailOutbox.findDue();
        for (Long id : emails) {
            try {
                mailOutbox.retry(id);
            } catch (RuntimeException e) {
                log.warn("Email retry {} failed: {}", id, e.getMessage());
            }
        }
        List<Long> messages = communicationService.findRetryDue();
        for (Long id : messages) {
            try {
                communicationService.retryMessage(id);
            } catch (RuntimeException e) {
                log.warn("Message retry {} failed: {}", id, e.getMessage());
            }
        }
        if (!emails.isEmpty() || !messages.isEmpty()) {
            log.info("Outbound retries: {} email(s), {} SMS/WhatsApp message(s)", emails.size(), messages.size());
        }
    }
}
