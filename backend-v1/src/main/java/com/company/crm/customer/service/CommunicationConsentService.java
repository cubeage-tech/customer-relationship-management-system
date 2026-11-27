package com.company.crm.customer.service;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.common.exception.ApiException;
import com.company.crm.communication.entity.Communication;
import com.company.crm.communication.event.InboundMessageRecordedEvent;
import com.company.crm.communication.repository.CommunicationRepo;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * NOT-7: a customer's communication preferences — marketing emails (unsubscribe link, FR-7.1c),
 * SMS (on until they opt out) and WhatsApp (only after they opt in). Staff record what the customer
 * said; a reply of STOP / START by SMS or WhatsApp updates it automatically. Automated messages,
 * campaigns and SMS sends check these before going out.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunicationConsentService {

    private static final Set<String> STOP = Set.of("STOP", "STOPALL", "UNSUBSCRIBE", "CANCEL", "END", "QUIT");
    private static final Set<String> START = Set.of("START", "UNSTOP", "SUBSCRIBE");

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final CommunicationRepo communicationRepo;
    private final AuditService auditService;

    public record Consent(Long customerId, boolean marketingEmail, boolean sms, boolean whatsapp,
                          LocalDateTime marketingOptOutAt, LocalDateTime smsOptOutAt, LocalDateTime whatsappOptInAt,
                          LocalDateTime updatedAt, String source) {}

    /** Any field left null is unchanged. */
    public record ConsentReq(Boolean marketingEmail, Boolean sms, Boolean whatsapp) {}

    @Transactional(readOnly = true)
    public Consent get(User user, Long customerId) {
        return toConsent(load(user, customerId));
    }

    @Transactional
    public Consent update(User user, Long customerId, ConsentReq req) {
        Customer c = load(user, customerId);
        List<String> changes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        if (req.marketingEmail() != null && req.marketingEmail() == c.isMarketingOptOut()) {
            c.setMarketingOptOut(!req.marketingEmail());
            c.setMarketingOptOutAt(req.marketingEmail() ? null : now);
            changes.add("marketing_email=" + onOff(req.marketingEmail()));
        }
        if (req.sms() != null && req.sms() == c.isSmsOptOut()) {
            c.setSmsOptOut(!req.sms());
            c.setSmsOptOutAt(req.sms() ? null : now);
            changes.add("sms=" + onOff(req.sms()));
        }
        if (req.whatsapp() != null && req.whatsapp() != c.isWhatsappOptIn()) {
            c.setWhatsappOptIn(req.whatsapp());
            c.setWhatsappOptInAt(req.whatsapp() ? now : null);
            changes.add("whatsapp=" + onOff(req.whatsapp()));
        }
        if (!changes.isEmpty()) {
            c.setConsentUpdatedAt(now);
            c.setConsentSource("staff");
            customerRepository.save(c);
            auditService.record(user, AuditAction.CUSTOMER_CONSENT_CHANGED, "customer", c.getId(), String.join(", ", changes));
        }
        return toConsent(c);
    }

    /** A customer replied STOP or START by SMS or WhatsApp: record it (also stopping campaigns on that channel). */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInbound(InboundMessageRecordedEvent event) {
        Communication message = communicationRepo.findById(event.communicationId()).orElse(null);
        if (message == null || message.getCustomer() == null || message.getBody() == null
                || (message.getChannel() != CommunicationChannel.SMS && message.getChannel() != CommunicationChannel.WHATSAPP)) {
            return;
        }
        String keyword = message.getBody().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        boolean stop = STOP.contains(keyword);
        boolean start = START.contains(keyword);
        if (!stop && !start) {
            return;
        }
        Customer c = message.getCustomer();
        LocalDateTime now = LocalDateTime.now();
        String change;
        if (message.getChannel() == CommunicationChannel.SMS) {
            if (c.isSmsOptOut() == stop) {
                return;
            }
            c.setSmsOptOut(stop);
            c.setSmsOptOutAt(stop ? now : null);
            change = "sms=" + (stop ? "off" : "on");
        } else {
            if (c.isWhatsappOptIn() == start) {
                return;
            }
            c.setWhatsappOptIn(start);
            c.setWhatsappOptInAt(start ? now : null);
            change = "whatsapp=" + (start ? "on" : "off");
        }
        c.setConsentUpdatedAt(now);
        c.setConsentSource(message.getChannel().getDbValue() + "_" + (stop ? "stop" : "start"));
        customerRepository.save(c);
        auditService.recordSystem(c.getTenant().getId(), AuditAction.CUSTOMER_CONSENT_CHANGED, "customer", c.getId(),
                change + " (customer replied " + keyword + ")");
        log.info("Customer {} replied {} by {}", c.getId(), keyword, message.getChannel().getDbValue());
    }

    /** Same access as viewing (and, for changes, editing) the customer. */
    private Customer load(User user, Long customerId) {
        customerService.getCustomer(user, customerId);
        return customerRepository.findById(customerId).orElseThrow(() -> ApiException.notFound("Customer not found"));
    }

    private static Consent toConsent(Customer c) {
        return new Consent(c.getId(), !c.isMarketingOptOut(), !c.isSmsOptOut(), c.isWhatsappOptIn(), c.getMarketingOptOutAt(),
                c.getSmsOptOutAt(), c.getWhatsappOptInAt(), c.getConsentUpdatedAt(), c.getConsentSource());
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }
}
