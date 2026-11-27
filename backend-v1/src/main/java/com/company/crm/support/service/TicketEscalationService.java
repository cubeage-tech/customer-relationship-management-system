package com.company.crm.support.service;

import java.util.ArrayList;
import com.company.crm.communication.service.SmsAlertService;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.notification.service.StaffNotifier;
import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.support.entity.ServiceTicket;
import com.company.crm.support.repository.ServiceTicketRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * FR-6.4c: escalates open tickets that are at risk of (last 25% of the window), or past, their SLA —
 * emails every active admin of the tenant (there is no separate Service Manager role) and the
 * assigned technician. Each ticket is escalated once for "at risk" and once for "breached".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketEscalationService {

    public enum Level { AT_RISK, BREACHED }

    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    private final ServiceTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final StaffNotifier staffNotifier;
    private final SmsAlertService smsAlertService;
    private final AuditService auditService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<Long> findBreached() {
        return ticketRepository.findBreachedUnescalatedIds(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Long> findAtRisk() {
        return ticketRepository.findAtRiskUnescalatedIds(LocalDateTime.now());
    }

    /** Sends one escalation in its own transaction; a no-op if another run already sent it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void escalate(Long ticketId, Level level) {
        LocalDateTime now = LocalDateTime.now();
        int claimed = level == Level.BREACHED
                ? ticketRepository.claimBreachEscalation(ticketId, now)
                : ticketRepository.claimAtRiskEscalation(ticketId, now);
        if (claimed == 0) {
            return;
        }
        ServiceTicket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) {
            return;
        }
        List<User> recipients = new ArrayList<>(userRepository.findByTenantId(ticket.getTenant().getId()).stream()
                .filter(u -> u.getRole().getName() == RoleType.ADMIN && u.getStatus() == AccountStatus.ACTIVE)
                .toList());
        if (ticket.getAssignedTechnician() != null) {
            recipients.add(ticket.getAssignedTechnician());
        }

        boolean breached = level == Level.BREACHED;
        String subject = (breached ? "SLA breached" : "SLA at risk") + ": ticket #" + ticket.getId() + " - " + ticket.getSubject();
        String body = (breached ? "This ticket has passed its SLA deadline." : "This ticket is close to its SLA deadline.") + "\n\n"
                + "Ticket #" + ticket.getId() + ": " + ticket.getSubject() + "\n"
                + "Customer: " + ticket.getCustomer().getCompanyName() + "\n"
                + "Priority: " + ticket.getPriority().getDbValue() + "\n"
                + "Status: " + ticket.getStatus().getDbValue().replace('_', ' ') + "\n"
                + "SLA due: " + DUE.format(ticket.getSlaDueAt()) + "\n"
                + "Assigned to: " + (ticket.getAssignedTechnician() != null ? ticket.getAssignedTechnician().getFullName() : "nobody (unassigned)");
        // NOT-5/NOT-6: in-app (and pushed) and email as the tenant chose; NOT-3: SMS if enabled.
        staffNotifier.notify(ticket.getTenant().getId(), NotificationEvent.TICKET_SLA_ESCALATED, recipients, subject, body,
                "service_ticket", ticket.getId(), null, "/service-tickets/" + ticket.getId());
        smsAlertService.onTicketEscalated(ticket, subject, recipients);
        auditService.recordSystem(ticket.getTenant().getId(), AuditAction.TICKET_SLA_ESCALATED, "service_ticket", ticket.getId(),
                "level=" + level.name().toLowerCase());
    }
}
