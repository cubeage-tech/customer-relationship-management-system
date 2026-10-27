package com.company.crm.notification.service;

import com.company.crm.communication.entity.Communication;
import com.company.crm.communication.service.CommunicationService;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.entity.CustomerContact;
import com.company.crm.notification.delivery.NotificationDeliveryLog;
import com.company.crm.notification.delivery.NotificationDeliveryLog.Recipient;
import com.company.crm.notification.settings.NotificationChannel;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.notification.settings.NotificationSettingsService;
import com.company.crm.quotation.entity.Quotation;
import com.company.crm.quotation.event.QuotationSentEvent;
import com.company.crm.quotation.mapper.QuotationMapper;
import com.company.crm.quotation.repository.QuotationRepository;
import com.company.crm.support.entity.ServiceTicket;
import com.company.crm.support.event.TicketStatusChangedEvent;
import com.company.crm.support.repository.ServiceTicketRepository;
import com.company.crm.tenant.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * NOT-2/NOT-6/NOT-7: messages to customers about their quotations and service requests, through
 * the channels the tenant chose — respecting the customer's consent (WhatsApp only after an
 * opt-in) — and logged on their timeline and in the delivery log (NOT-8). SMS goes through the
 * FR-5.3 SMS alerts.
 */
@Service
@RequiredArgsConstructor
public class CustomerNotifier {

    private final NotificationSettingsService settings;
    private final CommunicationService communicationService;
    private final NotificationDeliveryLog deliveryLog;
    private final QuotationRepository quotationRepository;
    private final ServiceTicketRepository ticketRepository;
    private final QuotationMapper quotationMapper;

    /**
     * An email about the event, if the tenant has the event's email on ({@code always}: service
     * emails the customer needs regardless, e.g. their request's confirmation).
     */
    @Transactional
    public Communication email(NotificationEvent event, Tenant tenant, Customer customer, String to, String subject, String body, boolean always) {
        if (!always && !settings.isEnabled(tenant.getId(), event, NotificationChannel.EMAIL)) {
            return null;
        }
        Communication email = communicationService.sendAutomatedEmail(tenant, customer, to, subject, body);
        deliveryLog.logMessage(tenant.getId(), event, "email", Recipient.customer(customer), email);
        return email;
    }

    /** A WhatsApp message about the event, if the tenant has it on and the customer opted in. */
    @Transactional
    public void whatsApp(NotificationEvent event, Tenant tenant, Customer customer, String body) {
        if (!settings.isEnabled(tenant.getId(), event, NotificationChannel.WHATSAPP)) {
            return;
        }
        if (!customer.isWhatsappOptIn()) {
            deliveryLog.skipped(tenant.getId(), event, "whatsapp", Recipient.customer(customer), "The customer hasn't opted in to WhatsApp");
            return;
        }
        Communication message = communicationService.sendAutomatedWhatsApp(tenant, customer, phoneOf(customer), body);
        deliveryLog.logMessage(tenant.getId(), event, "whatsapp", Recipient.customer(customer), message);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onQuotationSent(QuotationSentEvent event) {
        Quotation q = quotationRepository.findByIdAndTenantId(event.quotationId(), event.tenantId()).orElse(null);
        if (q == null || q.getViewToken() == null) {
            return;
        }
        whatsApp(NotificationEvent.QUOTATION_SENT, q.getTenant(), q.getCustomer(), String.format(
                "%s: your quotation %s for Rs %,.2f is ready. View it here: %s", q.getTenant().getCompanyName(),
                q.getQuotationNumber(), q.getGrandTotal(), quotationMapper.viewUrl(q)));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTicketStatusChanged(TicketStatusChangedEvent event) {
        ServiceTicket t = ticketRepository.findByIdAndTenantId(event.ticketId(), event.tenantId()).orElse(null);
        if (t == null) {
            return;
        }
        whatsApp(NotificationEvent.TICKET_STATUS_CHANGED, t.getTenant(), t.getCustomer(), t.getTenant().getCompanyName()
                + ": your service request " + t.getId() + " \"" + t.getSubject() + "\" is now " + event.status().getDbValue().replace('_', ' ') + ".");
    }

    /** The customer's own number, else the first contact person with one. */
    private static String phoneOf(Customer customer) {
        if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
            return customer.getPhone();
        }
        return customer.getContacts().stream().map(CustomerContact::getPhone).filter(p -> p != null && !p.isBlank()).findFirst().orElse(null);
    }
}
