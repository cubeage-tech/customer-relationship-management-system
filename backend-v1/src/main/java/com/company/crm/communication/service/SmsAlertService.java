package com.company.crm.communication.service;

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
            String phone = rule.getRecipient() == SmsAlertRecipient.CUSTOMER
                    ? customerPhone(quotation.getCustomer())
                    : phoneOf(quotation.getOwner());
            String body = String.format("%s: quotation %s for Rs %,.2f has been sent to %s.",
                    quotation.getTenant().getCompanyName(), quotation.getQuotationNumber(),
                    quotation.getGrandTotal(), quotation.getCustomer().getCompanyName());
            communicationService.sendAutomatedSms(quotation.getTenant(), quotation.getCustomer(), phone, body);
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
            String phone = rule.getRecipient() == SmsAlertRecipient.CUSTOMER
                    ? customerPhone(ticket.getCustomer())
                    : phoneOf(ticket.getAssignedTechnician());
            String body = String.format("%s: service ticket \"%s\" is now %s.",
                    ticket.getTenant().getCompanyName(), ticket.getSubject(), label(event.status()));
            communicationService.sendAutomatedSms(ticket.getTenant(), ticket.getCustomer(), phone, body);
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
            communicationService.sendAutomatedLeadSms(lead.getTenant(), lead, phoneOf(recipient), body);
        });
    }

    private static SmsAlertRecipient defaultRecipient(SmsAlertEvent event) {
        return event == SmsAlertEvent.FOLLOW_UP_DUE ? SmsAlertRecipient.INTERNAL : SmsAlertRecipient.CUSTOMER;
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
