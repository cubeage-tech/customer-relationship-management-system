package com.company.crm.communication.service;

import com.company.crm.tenant.entity.Tenant;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.notification.delivery.NotificationDeliveryLog.Recipient;
import com.company.crm.notification.delivery.NotificationDeliveryLog;
import com.company.crm.communication.entity.Communication;
import com.company.crm.common.enums.SmsAlertEvent;
import com.company.crm.common.enums.SmsAlertRecipient;
import com.company.crm.common.enums.TicketStatus;
import com.company.crm.common.exception.ApiException;
import com.company.crm.communication.dto.request.SmsAlertRuleReqDto;
import com.company.crm.communication.dto.responce.SmsAlertRuleResDto;
import com.company.crm.communication.entity.SmsAlertRule;
import com.company.crm.communication.repository.SmsAlertRuleRepository;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.entity.CustomerContact;
import com.company.crm.lead.entity.Lead;
import com.company.crm.quotation.entity.Quotation;
import com.company.crm.quotation.event.QuotationSentEvent;
import com.company.crm.quotation.repository.QuotationRepository;
import com.company.crm.support.entity.ServiceTicket;
import com.company.crm.support.event.TicketStatusChangedEvent;
import com.company.crm.support.repository.ServiceTicketRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * FR-5.3: SMS alerts for configurable events. Each tenant admin switches events on/off and picks
 * the recipient (the customer, or the internal owner/assignee). Alerts run after the triggering
 * transaction commits, and every attempt — sent or failed — is logged on the customer's timeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsAlertService {

    private final SmsAlertRuleRepository ruleRepository;
    private final QuotationRepository quotationRepository;
    private final ServiceTicketRepository ticketRepository;
    private final CommunicationService communicationService;
    private final NotificationDeliveryLog deliveryLog;

    @Transactional(readOnly = true)
    public List<SmsAlertRuleResDto> listRules(User currentUser) {
        Long tenantId = requireTenantId(currentUser);
        List<SmsAlertRule> saved = ruleRepository.findByTenantId(tenantId);
        return Arrays.stream(SmsAlertEvent.values())
                .map(event -> saved.stream().filter(r -> r.getEvent() == event).findFirst()
                        .map(r -> new SmsAlertRuleResDto(event.getDbValue(), r.isEnabled(), r.getRecipient().getDbValue()))
                        .orElse(new SmsAlertRuleResDto(event.getDbValue(), false, defaultRecipient(event).getDbValue())))
                .toList();
    }

    @Transactional
    public List<SmsAlertRuleResDto> updateRules(User currentUser, List<SmsAlertRuleReqDto> rules) {
        Long tenantId = requireTenantId(currentUser);
        for (SmsAlertRuleReqDto dto : rules) {
            SmsAlertEvent event = parse(() -> SmsAlertEvent.fromDbValue(dto.getEvent()), "Unknown SMS alert event: " + dto.getEvent());
            SmsAlertRecipient recipient = parse(() -> SmsAlertRecipient.fromDbValue(dto.getRecipient()), "Unknown recipient: " + dto.getRecipient());
            if (event == SmsAlertEvent.FOLLOW_UP_DUE && recipient != SmsAlertRecipient.INTERNAL) {
                throw ApiException.badRequest("Follow-up reminders can only be sent to the lead owner");
            }
            if (event == SmsAlertEvent.TICKET_ESCALATED && recipient != SmsAlertRecipient.INTERNAL) {
                throw ApiException.badRequest("SLA escalations can only be sent to the admins and the technician");
            }
            SmsAlertRule rule = ruleRepository.findById(new SmsAlertRule.Key(tenantId, event)).orElseGet(() -> {
                SmsAlertRule created = new SmsAlertRule();
                created.setTenantId(tenantId);
                created.setEvent(event);
                return created;
            });
            rule.setEnabled(dto.isEnabled());
            rule.setRecipient(recipient);
            ruleRepository.save(rule);
        }
        return listRules(currentUser);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onQuotationSent(QuotationSentEvent event) {
        activeRule(event.tenantId(), SmsAlertEvent.QUOTATION_SENT).ifPresent(rule -> {
            Quotation quotation = quotationRepository.findByIdAndTenantId(event.quotationId(), event.tenantId()).orElse(null);
            if (quotation == null) {
                return;
            }
            String body = String.format("%s: quotation %s for Rs %,.2f has been sent to %s.",
                    quotation.getTenant().getCompanyName(), quotation.getQuotationNumber(),
                    quotation.getGrandTotal(), quotation.getCustomer().getCompanyName());
            send(rule, NotificationEvent.QUOTATION_SENT, quotation.getTenant(), quotation.getCustomer(), quotation.getOwner(), body);
        });
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTicketStatusChanged(TicketStatusChangedEvent event) {
        activeRule(event.tenantId(), SmsAlertEvent.TICKET_STATUS_CHANGED).ifPresent(rule -> {
            ServiceTicket ticket = ticketRepository.findByIdAndTenantId(event.ticketId(), event.tenantId()).orElse(null);
            if (ticket == null) {
                return;
            }
            String body = String.format("%s: service ticket \"%s\" is now %s.",
                    ticket.getTenant().getCompanyName(), ticket.getSubject(), label(event.status()));
            send(rule, NotificationEvent.TICKET_STATUS_CHANGED, ticket.getTenant(), ticket.getCustomer(), ticket.getAssignedTechnician(), body);
        });
    }

    /**
     * FR-2.5/FR-5.3: called by the follow-up reminder job (already in its own transaction) — texts the
     * lead's owner if the tenant enabled the follow_up_due alert. Logged on the lead's timeline.
     */
    @Transactional
    public void onFollowUpDue(Lead lead, User recipient) {
        activeRule(lead.getTenant().getId(), SmsAlertEvent.FOLLOW_UP_DUE).ifPresent(rule -> {
            String body = String.format("%s: follow-up with %s (%s) is due.",
                    lead.getTenant().getCompanyName(), lead.getLeadName(), lead.getCompanyName());
            Communication sms = communicationService.sendAutomatedLeadSms(lead.getTenant(), lead, phoneOf(recipient), body);
            deliveryLog.logMessage(lead.getTenant().getId(), NotificationEvent.FOLLOW_UP_DUE, "sms", Recipient.user(recipient), sms);
        });
    }

    /** NOT-3: texts the admins and the technician that a ticket's SLA is at risk or breached (if the tenant enabled it). */
    @Transactional
    public void onTicketEscalated(ServiceTicket ticket, String text, java.util.Collection<User> recipients) {
        activeRule(ticket.getTenant().getId(), SmsAlertEvent.TICKET_ESCALATED).ifPresent(rule -> {
            for (User user : recipients) {
                Communication sms = communicationService.sendAutomatedSms(ticket.getTenant(), ticket.getCustomer(), phoneOf(user),
                        ticket.getTenant().getCompanyName() + ": " + text);
                deliveryLog.logMessage(ticket.getTenant().getId(), NotificationEvent.TICKET_SLA_ESCALATED, "sms", Recipient.user(user), sms);
            }
        });
    }

    /** NOT-6: whether the tenant has the event's SMS switched on. */
    @Transactional(readOnly = true)
    public boolean isEnabled(Long tenantId, SmsAlertEvent event) {
        return event != null && activeRule(tenantId, event).isPresent();
    }

    /**
     * One customer-event SMS: to the customer (unless they opted out of SMS, NOT-7) or to the
     * internal owner, logged on the customer's timeline and in the delivery log (NOT-8).
     */
    private void send(SmsAlertRule rule, NotificationEvent event, Tenant tenant, Customer customer, User internal, String body) {
        if (rule.getRecipient() == SmsAlertRecipient.CUSTOMER) {
            if (customer.isSmsOptOut()) {
                deliveryLog.skipped(tenant.getId(), event, "sms", Recipient.customer(customer), "The customer opted out of SMS");
                return;
            }
            Communication sms = communicationService.sendAutomatedSms(tenant, customer, customerPhone(customer), body);
            deliveryLog.logMessage(tenant.getId(), event, "sms", Recipient.customer(customer), sms);
        } else {
            Communication sms = communicationService.sendAutomatedSms(tenant, customer, phoneOf(internal), body);
            if (internal != null) {
                deliveryLog.logMessage(tenant.getId(), event, "sms", Recipient.user(internal), sms);
            }
        }
    }

    private static SmsAlertRecipient defaultRecipient(SmsAlertEvent event) {
        return event == SmsAlertEvent.FOLLOW_UP_DUE || event == SmsAlertEvent.TICKET_ESCALATED
                ? SmsAlertRecipient.INTERNAL : SmsAlertRecipient.CUSTOMER;
    }

    private Optional<SmsAlertRule> activeRule(Long tenantId, SmsAlertEvent event) {
        return ruleRepository.findById(new SmsAlertRule.Key(tenantId, event)).filter(SmsAlertRule::isEnabled);
    }

    /** The customer's own number, else the first contact person with one. */
    private static String customerPhone(Customer customer) {
        if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
            return customer.getPhone();
        }
        return customer.getContacts().stream()
                .map(CustomerContact::getPhone)
                .filter(p -> p != null && !p.isBlank())
                .findFirst().orElse(null);
    }

    private static String phoneOf(User user) {
        return user != null ? user.getPhone() : null;
    }

    private static String label(TicketStatus status) {
        String value = status.getDbValue().replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static <T> T parse(java.util.function.Supplier<T> parser, String message) {
        try {
            return parser.get();
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(message);
        }
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("SMS alerts are configured per tenant");
        }
        return currentUser.getTenant().getId();
    }
}
