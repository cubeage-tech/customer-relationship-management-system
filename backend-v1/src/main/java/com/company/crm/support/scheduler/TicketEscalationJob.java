package com.company.crm.support.scheduler;

import com.company.crm.support.service.TicketEscalationService;
import com.company.crm.support.service.TicketEscalationService.Level;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** FR-6.4c: every few minutes, escalate tickets that just went at risk of, or past, their SLA. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketEscalationJob {

    private final TicketEscalationService escalationService;

    @Scheduled(cron = "${app.ticket.sla-escalation-cron:0 */10 * * * *}")
    public void escalate() {
        // Breaches first: a ticket that skipped the at-risk window gets one breach email, not two.
        int count = run(escalationService.findBreached(), Level.BREACHED) + run(escalationService.findAtRisk(), Level.AT_RISK);
        if (count > 0) {
            log.info("SLA escalation: processed {} ticket(s)", count);
        }
    }

    private int run(List<Long> ticketIds, Level level) {
        for (Long id : ticketIds) {
            try {
                escalationService.escalate(id, level);
            } catch (RuntimeException e) {
                log.warn("SLA escalation for ticket {} failed: {}", id, e.getMessage());
            }
        }
        return ticketIds.size();
    }
}
