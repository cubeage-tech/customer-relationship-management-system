package com.company.crm.communication.service;

import com.company.crm.communication.event.InboundMessageRecordedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.company.crm.common.enums.CallOutcome;
import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.common.enums.CommunicationDirection;
import com.company.crm.common.enums.CommunicationStatus;
import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.mail.MailOutboxService;
import com.company.crm.common.mail.MailQueuedException;
import com.company.crm.common.mail.MailService;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.communication.dto.request.CallLogReqDto;
import com.company.crm.communication.dto.request.EmailSendReqDto;
import com.company.crm.communication.dto.request.SmsSendReqDto;
import com.company.crm.communication.dto.request.WhatsAppSendReqDto;
import com.company.crm.communication.dto.responce.CommunicationResDto;
import com.company.crm.communication.entity.Communication;
import com.company.crm.communication.integration.TwilioClient;
import com.company.crm.communication.repository.CommunicationMatchQuery;
import com.company.crm.communication.repository.CommunicationRepo;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.lead.entity.Lead;
import com.company.crm.lead.repository.LeadRepository;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.user.entity.User;
import com.company.crm.meeting.entity.Meeting;
import com.company.crm.meeting.repository.MeetingRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Module 5 — Communication Management. Every email, WhatsApp message, SMS and call is one
 * communication_logs row against a customer and/or lead (FR-5.1–5.4); the customer's rows,
 * newest first, are its timeline (FR-5.6). Records are tenant-scoped and follow the caller's
 * data scope on the customer/lead they belong to.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunicationService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final CommunicationRepo communicationRepo;
    private final MeetingRepository meetingRepository;
    private final CommunicationMatchQuery matchQuery;
    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final TenantRepository tenantRepository;
    private final DataScopeResolver dataScopeResolver;
    private final MailService mailService;
    private final TwilioClient twilioClient;
    private final PhoneNumbers phoneNumbers;
    private final ApplicationEventPublisher eventPublisher;

    /** The customer/lead a communication is logged against, after tenant and access checks. */
    private record Target(Tenant tenant, Customer customer, Lead lead) {}

    /** An inbound email/message from a provider, before we know whose it is. */
    public record InboundMessage(CommunicationChannel channel, String from, String subject, String body,
                                 String providerMessageId, Collection<String> inReplyTo, LocalDateTime occurredAt) {}

    // ==================== Timeline (FR-5.6) ====================

    @Transactional(readOnly = true)
    public List<CommunicationResDto> timeline(User currentUser, Long customerId, Long leadId) {
        Target target = resolveTarget(currentUser, customerId, leadId);
        boolean forCustomer = target.customer() != null && customerId != null;
        Long tenantId = target.tenant().getId();
        List<Communication> rows = forCustomer
                ? communicationRepo.findByTenantIdAndCustomerIdOrderByOccurredAtDesc(tenantId, target.customer().getId())
                : communicationRepo.findByTenantIdAndLeadIdOrderByOccurredAtDesc(tenantId, target.lead().getId());
        // FR-5.6: meetings (FR-5.5) join the same timeline, placed at their start time.
        List<Meeting> meetings = forCustomer
                ? meetingRepository.findByTenantIdAndCustomerIdOrderByStartsAtDesc(tenantId, target.customer().getId())
                : meetingRepository.findByTenantIdAndLeadIdOrderByStartsAtDesc(tenantId, target.lead().getId());
        return Stream.concat(rows.stream().map(this::toDto), meetings.stream().map(CommunicationService::meetingEntry))
                .sorted(Comparator.comparing(CommunicationResDto::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** UI-1: one entry of the unified inbox, with the customer or lead it belongs to. */
    public record InboxItem(CommunicationResDto entry, String recordType, Long recordId, String recordName) {}

    /**
     * UI-1 (SRS §11.1 Communications → Unified Inbox): the latest emails, WhatsApp messages, SMS and
     * calls across the customers and leads the caller may see, newest first; optionally one channel
     * or direction.
     */
    @Transactional(readOnly = true)
    public List<InboxItem> inbox(User currentUser, String channel, String direction, int limit) {
        Long tenantId = requireTenantId(currentUser);
        DataScope scope = dataScopeResolver.resolve(currentUser);
        int max = Math.max(1, Math.min(limit, 200));
        return communicationRepo.findTop500ByTenantIdOrderByOccurredAtDesc(tenantId).stream()
                .filter(c -> channel == null || channel.isBlank() || c.getChannel().getDbValue().equals(channel))
                .filter(c -> direction == null || direction.isBlank() || c.getDirection().getDbValue().equals(direction))
                .filter(c -> c.getCustomer() != null
                        ? scope.allows(c.getCustomer().getOwner() != null ? c.getCustomer().getOwner().getId() : null)
                        : c.getLead() != null && scope.allows(c.getLead().getOwner() != null ? c.getLead().getOwner().getId() : null))
                .limit(max)
                .map(c -> c.getCustomer() != null
                        ? new InboxItem(toDto(c), "customer", c.getCustomer().getId(), c.getCustomer().getCompanyName())
                        : new InboxItem(toDto(c), "lead", c.getLead().getId(), c.getLead().getLeadName()))
                .toList();
    }

    // ==================== Calls (FR-5.4) ====================

    @Transactional
    public CommunicationResDto logCall(User currentUser, CallLogReqDto dto) {
        Target target = resolveTarget(currentUser, dto.getCustomerId(), dto.getLeadId());
        Communication call = newLog(target, CommunicationChannel.CALL, parseDirection(dto.getDirection()), currentUser);
        call.setStatus(CommunicationStatus.LOGGED);
        call.setCallOutcome(parseOutcome(dto.getOutcome()));
        call.setCallDurationSeconds(dto.getDurationSeconds());
        call.setBody(blankToNull(dto.getNotes()));
        call.setCounterpart(phoneNumbers.normalizeOrNull(dto.getPhone()));
        if (dto.getOccurredAt() != null) {
            if (dto.getOccurredAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
                throw ApiException.badRequest("A call cannot be logged in the future");
            }
            call.setOccurredAt(dto.getOccurredAt());
        }
        return toDto(communicationRepo.save(call));
    }

    // ==================== Email (FR-5.1) ====================

    /** Sends now and logs the attempt; a delivery failure is recorded as FAILED rather than thrown. */
    @Transactional
    public CommunicationResDto sendEmail(User currentUser, EmailSendReqDto dto) {
        Target target = resolveTarget(currentUser, dto.getCustomerId(), dto.getLeadId());
        Communication email = newLog(target, CommunicationChannel.EMAIL, CommunicationDirection.OUTBOUND, currentUser);
        email.setStatus(CommunicationStatus.QUEUED);
        email.setSubject(dto.getSubject().trim());
        email.setBody(dto.getBody());
        email.setCounterpart(dto.getTo().trim().toLowerCase());
        email = communicationRepo.save(email);

        String messageId = "<crm." + email.getId() + "." + UUID.randomUUID() + "@smartcrm>";
        email.setProviderMessageId(messageId);
        try {
            mailService.sendCustomerEmail(email.getCounterpart(), email.getSubject(), email.getBody(), messageId);
            email.setStatus(CommunicationStatus.SENT);
        } catch (MailQueuedException e) {
            log.warn("Email {} queued for retry: {}", email.getId(), e.getMessage());
            email.setStatus(CommunicationStatus.QUEUED);
            email.setErrorMessage(truncate("Mail server unavailable — retrying automatically"));
        } catch (MailException | MessagingException e) {
            log.warn("Customer email {} failed: {}", email.getId(), e.getMessage());
            email.setStatus(CommunicationStatus.FAILED);
            email.setErrorMessage(truncate("Email could not be sent: " + e.getMessage()));
        }
        return toDto(communicationRepo.save(email));
    }

    /**
     * Sends one email on the system's behalf (FR-4.5 quotation link) and logs it on the customer's
     * timeline. Never throws: a missing address or a delivery failure is logged as FAILED.
     */
    @Transactional
    public Communication sendAutomatedEmail(Tenant tenant, Customer customer, String to, String subject, String body) {
        return sendAutomatedEmail(tenant, customer, null, to, subject, body, null);
    }

    /** FR-5.5: same, for a customer or lead, optionally carrying an iCalendar invite ({@code ics}). */
    @Transactional
    public Communication sendAutomatedEmail(Tenant tenant, Customer customer, Lead lead, String to, String subject, String body, String ics) {
        Communication email = newLog(new Target(tenant, customer, lead), CommunicationChannel.EMAIL, CommunicationDirection.OUTBOUND, null);
        email.setSubject(subject);
        email.setBody(body);
        email.setCounterpart(to == null || to.isBlank() ? null : to.trim().toLowerCase());
        if (email.getCounterpart() == null) {
            email.setStatus(CommunicationStatus.FAILED);
            email.setErrorMessage("No email address on record");
            return communicationRepo.save(email);
        }
        email.setStatus(CommunicationStatus.QUEUED);
        email = communicationRepo.save(email);
        String messageId = "<crm." + email.getId() + "." + UUID.randomUUID() + "@smartcrm>";
        email.setProviderMessageId(messageId);
        try {
            if (ics == null) {
                mailService.sendCustomerEmail(email.getCounterpart(), subject, body, messageId);
            } else {
                mailService.sendCustomerEmail(email.getCounterpart(), subject, body, messageId, ics);
            }
            email.setStatus(CommunicationStatus.SENT);
        } catch (MailQueuedException e) {
            log.warn("Email {} queued for retry: {}", email.getId(), e.getMessage());
            email.setStatus(CommunicationStatus.QUEUED);
            email.setErrorMessage(truncate("Mail server unavailable — retrying automatically"));
        } catch (MailException | MessagingException e) {
            log.warn("Automated email {} failed: {}", email.getId(), e.getMessage());
            email.setStatus(CommunicationStatus.FAILED);
            email.setErrorMessage(truncate("Email could not be sent: " + e.getMessage()));
        }
        return communicationRepo.save(email);
    }

    /**
     * FR-7.1c: one campaign email (HTML + plain-text, with an unsubscribe link), logged on the
     * customer's timeline. Never throws: a delivery failure is logged as FAILED and returned.
     */
    @Transactional
    public Communication sendMarketingEmail(Tenant tenant, Customer customer, String to, String subject,
                                            String text, String html, String unsubscribeUrl) {
        Communication email = newLog(new Target(tenant, customer, null), CommunicationChannel.EMAIL, CommunicationDirection.OUTBOUND, null);
        email.setSubject(subject);
        email.setBody(text);
        email.setCounterpart(to.trim().toLowerCase());
        email.setStatus(CommunicationStatus.QUEUED);
        email = communicationRepo.save(email);
        String messageId = "<crm." + email.getId() + "." + UUID.randomUUID() + "@smartcrm>";
        email.setProviderMessageId(messageId);
        try {
            mailService.sendMarketingEmail(email.getCounterpart(), subject, text, html, messageId, unsubscribeUrl);
            email.setStatus(CommunicationStatus.SENT);
        } catch (MailQueuedException e) {
            log.warn("Email {} queued for retry: {}", email.getId(), e.getMessage());
            email.setStatus(CommunicationStatus.QUEUED);
            email.setErrorMessage(truncate("Mail server unavailable — retrying automatically"));
        } catch (MailException | MessagingException e) {
            log.warn("Campaign email {} failed: {}", email.getId(), e.getMessage());
            email.setStatus(CommunicationStatus.FAILED);
            email.setErrorMessage(truncate("Email could not be sent: " + e.getMessage()));
        }
        return communicationRepo.save(email);
    }

    // ==================== WhatsApp (FR-5.2) ====================

    @Transactional
    public CommunicationResDto sendWhatsApp(User currentUser, WhatsAppSendReqDto dto) {
        if (!twilioClient.isWhatsAppEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WhatsApp is not configured for this CRM", "CHANNEL_NOT_CONFIGURED");
        }
        Target target = resolveTarget(currentUser, dto.getCustomerId(), dto.getLeadId());
        Communication message = newLog(target, CommunicationChannel.WHATSAPP, CommunicationDirection.OUTBOUND, currentUser);
        message.setCounterpart(phoneNumbers.toE164(dto.getTo()));
        message.setBody(dto.getBody());
        applySendResult(message, () -> twilioClient.sendWhatsApp(message.getCounterpart(), dto.getBody()));
        return toDto(communicationRepo.save(message));
    }

    /**
     * API-G06: a user sends an SMS to a customer or lead; logged on its timeline. A temporary
     * provider failure is queued and resent automatically (NFR-7.4a).
     */
    @Transactional
    public CommunicationResDto sendSms(User currentUser, SmsSendReqDto dto) {
        if (!twilioClient.isSmsEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS is not configured for this CRM", "CHANNEL_NOT_CONFIGURED");
        }
        Target target = resolveTarget(currentUser, dto.getCustomerId(), dto.getLeadId());
        if (target.customer() != null && target.customer().isSmsOptOut()) {
            // NOT-7: they replied STOP or asked not to be texted.
            throw new ApiException(HttpStatus.CONFLICT, "This customer has opted out of SMS", "CUSTOMER_OPTED_OUT");
        }
        Communication sms = newLog(target, CommunicationChannel.SMS, CommunicationDirection.OUTBOUND, currentUser);
        sms.setCounterpart(phoneNumbers.toE164(dto.getTo()));
        sms.setBody(dto.getBody());
        applySendResult(sms, () -> twilioClient.sendSms(sms.getCounterpart(), dto.getBody()));
        return toDto(communicationRepo.save(sms));
    }

    /**
     * Sends one SMS on the system's behalf (FR-5.3 alerts) and logs it with its outcome. Never throws:
     * an unconfigured provider or a missing number is logged as FAILED so admins can see why.
     */
    @Transactional
    public Communication sendAutomatedSms(Tenant tenant, Customer customer, String rawPhone, String body) {
        return sendAutomatedSms(new Target(tenant, customer, null), rawPhone, body);
    }

    /** FR-5.3 for lead events (follow-up due): same as above, logged on the lead's timeline. */
    @Transactional
    public Communication sendAutomatedLeadSms(Tenant tenant, Lead lead, String rawPhone, String body) {
        return sendAutomatedSms(new Target(tenant, null, lead), rawPhone, body);
    }

    /**
     * NOT-2: one WhatsApp message on the system's behalf (customer updates, WhatsApp campaigns), logged
     * with its outcome like {@link #sendAutomatedSms}. The caller checks the customer opted in (NOT-7).
     */
    @Transactional
    public Communication sendAutomatedWhatsApp(Tenant tenant, Customer customer, String rawPhone, String body) {
        Communication message = newLog(new Target(tenant, customer, null), CommunicationChannel.WHATSAPP, CommunicationDirection.OUTBOUND, null);
        message.setBody(body);
        message.setCounterpart(phoneNumbers.normalizeOrNull(rawPhone));
        if (!twilioClient.isWhatsAppEnabled()) {
            message.setStatus(CommunicationStatus.FAILED);
            message.setErrorMessage("WhatsApp is not configured for this CRM");
        } else if (message.getCounterpart() == null) {
            message.setStatus(CommunicationStatus.FAILED);
            message.setErrorMessage("No valid phone number on record");
        } else {
            applySendResult(message, () -> twilioClient.sendWhatsApp(message.getCounterpart(), body));
        }
        return communicationRepo.save(message);
    }

    private Communication sendAutomatedSms(Target target, String rawPhone, String body) {
        Communication sms = newLog(target, CommunicationChannel.SMS, CommunicationDirection.OUTBOUND, null);
        sms.setBody(body);
        sms.setCounterpart(phoneNumbers.normalizeOrNull(rawPhone));
        if (!twilioClient.isSmsEnabled()) {
            sms.setStatus(CommunicationStatus.FAILED);
            sms.setErrorMessage("SMS is not configured for this CRM");
        } else if (sms.getCounterpart() == null) {
            sms.setStatus(CommunicationStatus.FAILED);
            sms.setErrorMessage("No valid phone number on record");
        } else {
            applySendResult(sms, () -> twilioClient.sendSms(sms.getCounterpart(), body));
        }
        return communicationRepo.save(sms);
    }

    // ==================== Inbound + delivery callbacks ====================

    /**
     * Logs an inbound email/WhatsApp/SMS against its customer or lead. Owner, in order: the message
     * it replies to; the latest message we sent to that address/number; a unique contact match.
     * Ambiguous or unknown senders and duplicates are skipped (returns empty).
     */
    @Transactional
    public Optional<Communication> recordInbound(InboundMessage message) {
        if (message.providerMessageId() != null && communicationRepo.findByProviderMessageId(message.providerMessageId()).isPresent()) {
            return Optional.empty();
        }
        String from = message.channel() == CommunicationChannel.EMAIL
                ? message.from().trim().toLowerCase()
                : phoneNumbers.normalizeOrNull(message.from());
        if (from == null) {
            return Optional.empty();
        }
        Optional<Target> owner = ownerOfReply(message.inReplyTo())
                .or(() -> communicationRepo.findFirstByChannelAndDirectionAndCounterpartHashOrderByOccurredAtDesc(
                        message.channel(), CommunicationDirection.OUTBOUND, DataEncryption.get().counterpartIndex(from)).map(this::targetOf))
                .or(() -> uniqueMatch(message.channel() == CommunicationChannel.EMAIL ? matchQuery.byEmail(from) : matchQuery.byPhone(from)));
        if (owner.isEmpty()) {
            log.info("Inbound {} from an unknown or ambiguous sender was not logged", message.channel().getDbValue());
            return Optional.empty();
        }
        Communication inbound = newLog(owner.get(), message.channel(), CommunicationDirection.INBOUND, null);
        inbound.setStatus(CommunicationStatus.RECEIVED);
        inbound.setCounterpart(from);
        inbound.setSubject(message.subject() != null ? truncate(message.subject(), 255) : null);
        inbound.setBody(message.body());
        inbound.setProviderMessageId(message.providerMessageId());
        if (message.occurredAt() != null) {
            inbound.setOccurredAt(message.occurredAt());
        }
        Communication saved = communicationRepo.save(inbound);
        eventPublisher.publishEvent(new InboundMessageRecordedEvent(saved.getTenant().getId(), saved.getId()));
        return Optional.of(saved);
    }

    /** A finished phone call reported by the telephony provider (FR-5.4b). */
    public record CallRecord(String providerCallId, String from, String to, boolean inbound,
                             CallOutcome outcome, Integer durationSeconds, LocalDateTime endedAt) {}

    /**
     * FR-5.4b: logs a call automatically from the telephony provider, against the customer or lead
     * whose number it was (the caller for inbound calls, the callee for outbound). Matched like
     * inbound messages: the latest call to that number, else a unique contact match. Unknown or
     * ambiguous numbers and duplicate callbacks are skipped (returns empty).
     */
    @Transactional
    public Optional<Communication> recordCall(CallRecord call) {
        if (call.providerCallId() != null && communicationRepo.findByProviderMessageId(call.providerCallId()).isPresent()) {
            return Optional.empty();
        }
        String counterpart = phoneNumbers.normalizeOrNull(call.inbound() ? call.from() : call.to());
        if (counterpart == null) {
            return Optional.empty();
        }
        Optional<Target> owner = communicationRepo.findFirstByChannelAndDirectionAndCounterpartHashOrderByOccurredAtDesc(
                        CommunicationChannel.CALL, CommunicationDirection.OUTBOUND, DataEncryption.get().counterpartIndex(counterpart)).map(this::targetOf)
                .or(() -> uniqueMatch(matchQuery.byPhone(counterpart)));
        if (owner.isEmpty()) {
            log.info("Automatic call log skipped: unknown or ambiguous number");
            return Optional.empty();
        }
        Communication row = newLog(owner.get(), CommunicationChannel.CALL,
                call.inbound() ? CommunicationDirection.INBOUND : CommunicationDirection.OUTBOUND, null);
        row.setStatus(CommunicationStatus.LOGGED);
        row.setCounterpart(counterpart);
        row.setCallOutcome(call.outcome());
        row.setCallDurationSeconds(call.durationSeconds());
        row.setBody("Logged automatically by the phone system.");
        row.setProviderMessageId(call.providerCallId());
        LocalDateTime ended = call.endedAt() != null ? call.endedAt() : LocalDateTime.now();
        row.setOccurredAt(call.durationSeconds() != null ? ended.minusSeconds(call.durationSeconds()) : ended);
        return Optional.of(communicationRepo.save(row));
    }

    /** Twilio status callback: queued → sent → delivered → read, or failed. Never moves backwards. */
    @Transactional
    public void updateDeliveryStatus(String providerMessageId, String providerStatus, String errorCode) {
        communicationRepo.findByProviderMessageId(providerMessageId).ifPresent(row -> {
            CommunicationStatus next = mapProviderStatus(providerStatus);
            if (next == CommunicationStatus.FAILED) {
                row.setStatus(next);
                row.setErrorMessage(truncate("Delivery failed" + (errorCode != null && !errorCode.isBlank() ? " (provider error " + errorCode + ")" : "")));
            } else if (rank(next) > rank(row.getStatus())) {
                row.setStatus(next);
            }
            communicationRepo.save(row);
        });
    }

    // ==================== Helpers ====================

    private void applySendResult(Communication row, java.util.function.Supplier<TwilioClient.SendResult> send) {
        try {
            TwilioClient.SendResult result = send.get();
            row.setProviderMessageId(result.sid());
            row.setStatus(mapProviderStatus(result.status()));
            row.setErrorMessage(null);
            row.setNextRetryAt(null);
        } catch (TwilioClient.TwilioException e) {
            if (e.isRetryable() && row.getRetryCount() < RETRY_BACKOFF_MINUTES.length) {
                // NFR-7.4a: temporary provider trouble — keep it queued and resend later.
                row.setStatus(CommunicationStatus.QUEUED);
                row.setErrorMessage(truncate(e.getMessage() + " — retrying automatically"));
                row.setNextRetryAt(LocalDateTime.now().plusMinutes(RETRY_BACKOFF_MINUTES[row.getRetryCount()]));
            } else {
                row.setStatus(CommunicationStatus.FAILED);
                row.setErrorMessage(truncate(e.getMessage()));
                row.setNextRetryAt(null);
            }
        }
    }

    // ==================== NFR-7.4a retries ====================

    /** Minutes before resend n+1 of an SMS/WhatsApp message after a temporary failure. */
    private static final int[] RETRY_BACKOFF_MINUTES = {1, 5, 15, 60, 180};

    @Transactional(readOnly = true)
    public List<Long> findRetryDue() {
        return communicationRepo.findRetryDueIds(LocalDateTime.now());
    }

    /** Resends one queued SMS/WhatsApp message (own transaction: each outcome is saved on its own). */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void retryMessage(Long id) {
        Communication row = communicationRepo.findById(id).orElse(null);
        if (row == null || row.getNextRetryAt() == null
                || communicationRepo.leaseRetry(id, row.getNextRetryAt(), LocalDateTime.now().plusMinutes(10)) == 0) {
            return;
        }
        Communication msg = communicationRepo.findById(id).orElseThrow();
        msg.setRetryCount(msg.getRetryCount() + 1);
        if (msg.getChannel() == CommunicationChannel.SMS) {
            applySendResult(msg, () -> twilioClient.sendSms(msg.getCounterpart(), msg.getBody()));
        } else if (msg.getChannel() == CommunicationChannel.WHATSAPP) {
            applySendResult(msg, () -> twilioClient.sendWhatsApp(msg.getCounterpart(), msg.getBody()));
        } else {
            msg.setNextRetryAt(null);
        }
        communicationRepo.save(msg);
    }

    /** NFR-7.4a: an email held in the mail outbox was finally delivered, or given up on. */
    @org.springframework.transaction.event.TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onQueuedEmailOutcome(MailOutboxService.Outcome outcome) {
        if (outcome.messageId() == null) {
            return;
        }
        communicationRepo.findByProviderMessageId(outcome.messageId()).ifPresent(email -> {
            email.setStatus(outcome.delivered() ? CommunicationStatus.SENT : CommunicationStatus.FAILED);
            email.setErrorMessage(outcome.delivered() ? null : truncate("Email could not be sent after several attempts: " + outcome.error()));
            communicationRepo.save(email);
        });
    }

    private Target resolveTarget(User currentUser, Long customerId, Long leadId) {
        if (customerId == null && leadId == null) {
            throw ApiException.badRequest("Choose the customer or lead this communication belongs to");
        }
        Long tenantId = requireTenantId(currentUser);
        Customer customer = null;
        Lead lead = null;
        if (customerId != null) {
            customer = customerRepository.findByIdAndTenantId(customerId, tenantId)
                    .orElseThrow(() -> ApiException.notFound("Customer not found"));
            dataScopeResolver.assertCanAccess(currentUser, customer.getOwner() != null ? customer.getOwner().getId() : null, "customer");
        }
        if (leadId != null) {
            lead = leadRepository.findByIdAndTenantId(leadId, tenantId)
                    .orElseThrow(() -> ApiException.notFound("Lead not found"));
            dataScopeResolver.assertCanAccess(currentUser, lead.getOwner() != null ? lead.getOwner().getId() : null, "lead");
            // A converted lead's history also belongs on its customer's timeline.
            if (customer == null && lead.getConvertedCustomer() != null) {
                customer = lead.getConvertedCustomer();
            }
        }
        return new Target(currentUser.getTenant(), customer, lead);
    }

    private Optional<Target> ownerOfReply(Collection<String> inReplyTo) {
        if (inReplyTo == null || inReplyTo.isEmpty()) {
            return Optional.empty();
        }
        return communicationRepo.findByProviderMessageIdIn(inReplyTo).stream().findFirst().map(this::targetOf);
    }

    private Target targetOf(Communication row) {
        return new Target(row.getTenant(), row.getCustomer(), row.getLead());
    }

    /** Accept a contact match only when it points at exactly one customer (or one lead) in one tenant. */
    private Optional<Target> uniqueMatch(List<CommunicationMatchQuery.Match> matches) {
        if (matches.stream().map(CommunicationMatchQuery.Match::tenantId).distinct().count() != 1) {
            return Optional.empty();
        }
        Long tenantId = matches.get(0).tenantId();
        List<Long> customers = matches.stream().map(CommunicationMatchQuery.Match::customerId).filter(Objects::nonNull).distinct().toList();
        List<Long> leads = matches.stream().map(CommunicationMatchQuery.Match::leadId).filter(Objects::nonNull).distinct().toList();
        Tenant tenant = tenantRepository.getReferenceById(tenantId);
        if (customers.size() == 1) {
            return Optional.of(new Target(tenant, customerRepository.getReferenceById(customers.get(0)), null));
        }
        if (customers.isEmpty() && leads.size() == 1) {
            return Optional.of(new Target(tenant, null, leadRepository.getReferenceById(leads.get(0))));
        }
        return Optional.empty();
    }

    private Communication newLog(Target target, CommunicationChannel channel, CommunicationDirection direction, User createdBy) {
        Communication row = new Communication();
        row.setTenant(target.tenant());
        row.setCustomer(target.customer());
        row.setLead(target.lead());
        row.setChannel(channel);
        row.setDirection(direction);
        row.setCreatedBy(createdBy);
        return row;
    }

    private CommunicationResDto toDto(Communication c) {
        return new CommunicationResDto(
                c.getId(),
                c.getCustomer() != null ? c.getCustomer().getId() : null,
                c.getLead() != null ? c.getLead().getId() : null,
                c.getChannel().getDbValue(),
                c.getDirection().getDbValue(),
                c.getStatus().getDbValue(),
                c.getSubject(),
                c.getBody(),
                c.getCounterpart(),
                c.getErrorMessage(),
                c.getCallOutcome() != null ? c.getCallOutcome().getDbValue() : null,
                c.getCallDurationSeconds(),
                c.getOccurredAt(),
                c.getCreatedBy() != null ? c.getCreatedBy().getFullName() : null);
    }

    /** FR-5.6: a meeting as a timeline entry — channel "meeting", status scheduled/cancelled, at its start time. */
    private static CommunicationResDto meetingEntry(Meeting m) {
        String body = Stream.of(m.getLocation() != null ? "Where: " + m.getLocation() : null, m.getNotes())
                .filter(Objects::nonNull).collect(Collectors.joining("\n"));
        return new CommunicationResDto(
                m.getId(),
                m.getCustomer() != null ? m.getCustomer().getId() : null,
                m.getLead() != null ? m.getLead().getId() : null,
                "meeting",
                null,
                m.getStatus(),
                m.getTitle(),
                body.isEmpty() ? null : body,
                null,
                null,
                null,
                (int) Duration.between(m.getStartsAt(), m.getEndsAt()).toSeconds(),
                m.getStartsAt(),
                m.getOrganizer().getFullName());
    }

    private static CommunicationStatus mapProviderStatus(String providerStatus) {
        return switch (providerStatus == null ? "" : providerStatus.toLowerCase()) {
            case "sent" -> CommunicationStatus.SENT;
            case "delivered" -> CommunicationStatus.DELIVERED;
            case "read" -> CommunicationStatus.READ;
            case "failed", "undelivered", "canceled" -> CommunicationStatus.FAILED;
            default -> CommunicationStatus.QUEUED; // accepted, queued, sending, scheduled
        };
    }

    private static int rank(CommunicationStatus status) {
        return switch (status) {
            case QUEUED -> 1;
            case SENT -> 2;
            case DELIVERED -> 3;
            case READ -> 4;
            default -> 0;
        };
    }

    private CommunicationDirection parseDirection(String raw) {
        try {
            return CommunicationDirection.fromDbValue(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown direction: " + raw);
        }
    }

    private CallOutcome parseOutcome(String raw) {
        try {
            return CallOutcome.fromDbValue(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown call outcome: " + raw);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value) {
        return truncate(value, MAX_ERROR_LENGTH);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Communications are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
