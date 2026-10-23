package com.company.crm.support.service;

import com.company.crm.communication.service.CommunicationService;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.entity.CustomerContact;
import com.company.crm.support.entity.ServiceTicket;
import com.company.crm.support.event.TicketFeedbackRequestedEvent;
import com.company.crm.support.repository.ServiceTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * FR-6.1/FR-6.5: emails to the customer about their service request — the confirmation of a
 * support-form request, and the feedback link once it is resolved. Logged on the customer's
 * communication timeline; a missing address is logged as failed.
 */
@Service
@RequiredArgsConstructor
public class TicketCustomerMessenger {

    private final ServiceTicketRepository ticketRepository;
    private final CommunicationService communicationService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

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
        communicationService.sendAutomatedEmail(ticket.getTenant(), ticket.getCustomer(), customerEmail(ticket),
                "How was our service? Ticket #" + ticket.getId(), body);
    }

    /** FR-6.1: confirms a request raised through the support form (called in its own transaction). */
    @Transactional
    public void confirmSupportRequest(ServiceTicket ticket) {
        String company = ticket.getTenant().getCompanyName();
        String body = "Hello,\n\nWe've received your service request and created ticket #" + ticket.getId() + ":\n\n"
                + ticket.getSubject() + "\nPriority: " + ticket.getPriority().getDbValue() + "\n\n"
                + "Our team will get back to you. Reply to this email to add details.\n\nThank you,\n" + company;
        communicationService.sendAutomatedEmail(ticket.getTenant(), ticket.getCustomer(), customerEmail(ticket),
                "We've received your request — ticket #" + ticket.getId(), body);
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
