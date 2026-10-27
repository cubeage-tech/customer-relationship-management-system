package com.company.crm.support.service;

import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.notification.service.CustomerNotifier;
import com.company.crm.common.enums.TicketStatus;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.entity.CustomerContact;
import com.company.crm.support.entity.ServiceTicket;
import com.company.crm.support.event.TicketFeedbackRequestedEvent;
import com.company.crm.support.event.TicketStatusChangedEvent;
import com.company.crm.support.repository.ServiceTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * FR-6.1/FR-6.5: emails to the customer about their service request — the confirmation of a
 * support-form request, NOT-1 progress updates (assigned, in progress, closed), and the feedback
 * link once it is resolved. Logged on the customer's communication timeline; a missing address is
 * logged as failed.
 */
@Service
@RequiredArgsConstructor
public class TicketCustomerMessenger {

    private final ServiceTicketRepository ticketRepository;
    private final CustomerNotifier customerNotifier;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /** NOT-1: tells the customer their request moved on. Resolved is covered by the feedback email. */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStatusChanged(TicketStatusChangedEvent event) {
        String update = switch (event.status()) {
            case ASSIGNED -> "has been assigned to a technician";
            case IN_PROGRESS -> "is now being worked on";
            case CLOSED -> "has been closed";
            default -> null;
        };
        ServiceTicket ticket = update == null ? null : ticketRepository.findByIdAndTenantId(event.ticketId(), event.tenantId()).orElse(null);
        if (ticket == null) {
            return;
        }
        String company = ticket.getTenant().getCompanyName();
        String technician = event.status() != TicketStatus.CLOSED && ticket.getAssignedTechnician() != null
                ? " (" + ticket.getAssignedTechnician().getFullName() + ")" : "";
        String body = "Hello,\n\nYour service request \"" + ticket.getSubject() + "\" (request " + ticket.getId() + ") " + update + technician + ".\n\n"
                + "Reply to this email if you have anything to add.\n\nThank you,\n" + company;
        // NOT-6: unless the tenant switched progress emails off (Settings → Notifications).
        customerNotifier.email(NotificationEvent.TICKET_STATUS_CHANGED, ticket.getTenant(), ticket.getCustomer(), customerEmail(ticket),
                "Update on your service request " + ticket.getId() + ": " + event.status().getDbValue().replace('_', ' '), body, false);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFeedbackRequested(TicketFeedbackRequestedEvent event) {
        ServiceTicket ticket = ticketRepository.findByIdAndTenantId(event.ticketId(), event.tenantId()).orElse(null);
        if (ticket == null || ticket.getFeedbackToken() == null) {
            return;
        }
        String company = ticket.getTenant().getCompanyName();
        String link = frontendUrl + "/feedback/" + ticket.getFeedbackToken();
        String body = "Hello,\n\nYour service request \"" + ticket.getSubject() + "\" (ticket #" + ticket.getId() + ") has been resolved"
                + (ticket.getAssignedTechnician() != null ? " by " + ticket.getAssignedTechnician().getFullName() : "") + ".\n\n"
                + "How did we do? Rate the service in one click:\n" + link + "\n\nThank you,\n" + company;
        customerNotifier.email(NotificationEvent.TICKET_STATUS_CHANGED, ticket.getTenant(), ticket.getCustomer(), customerEmail(ticket),
                "How was our service? Ticket #" + ticket.getId(), body, true);
    }

    /** FR-6.1: confirms a request raised through the support form (called in its own transaction). */
    @Transactional
    public void confirmSupportRequest(ServiceTicket ticket) {
        String company = ticket.getTenant().getCompanyName();
        String body = "Hello,\n\nWe've received your service request and created ticket #" + ticket.getId() + ":\n\n"
                + ticket.getSubject() + "\nPriority: " + ticket.getPriority().getDbValue() + "\n\n"
                + "Our team will get back to you. Reply to this email to add details.\n\nThank you,\n" + company;
        customerNotifier.email(NotificationEvent.TICKET_STATUS_CHANGED, ticket.getTenant(), ticket.getCustomer(), customerEmail(ticket),
                "We've received your request — ticket #" + ticket.getId(), body, true);
    }

    /** Who to write to: the support-form reporter, else the customer's (or first contact's) email. */
    static String customerEmail(ServiceTicket ticket) {
        if (ticket.getReporterEmail() != null) {
            return ticket.getReporterEmail();
        }
        Customer c = ticket.getCustomer();
        if (c.getEmail() != null && !c.getEmail().isBlank()) {
            return c.getEmail();
        }
        return c.getContacts().stream().map(CustomerContact::getEmail).filter(e -> e != null && !e.isBlank()).findFirst().orElse(null);
    }
}
