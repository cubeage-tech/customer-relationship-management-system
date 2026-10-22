package com.company.crm.communication.service;

import com.company.crm.common.enums.CallOutcome;
import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.common.enums.CommunicationDirection;
import com.company.crm.common.enums.CommunicationStatus;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.mail.MailService;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.communication.dto.request.CallLogReqDto;
import com.company.crm.communication.dto.request.EmailSendReqDto;
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
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
    private final CommunicationMatchQuery matchQuery;
    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final TenantRepository tenantRepository;
    private final DataScopeResolver dataScopeResolver;
    private final MailService mailService;
    private final TwilioClient twilioClient;
    private final PhoneNumbers phoneNumbers;

    /** The customer/lead a communication is logged against, after tenant and access checks. */
    private record Target(Tenant tenant, Customer customer, Lead lead) {}

    /** An inbound email/message from a provider, before we know whose it is. */
    public record InboundMessage(CommunicationChannel channel, String from, String subject, String body,
                                 String providerMessageId, Collection<String> inReplyTo, LocalDateTime occurredAt) {}

    // ==================== Timeline (FR-5.6) ====================

    @Transactional(readOnly = true)
    public List<CommunicationResDto> timeline(User currentUser, Long customerId, Long leadId) {
        Target target = resolveTarget(currentUser, customerId, leadId);
        List<Communication> rows = target.customer() != null && customerId != null
                ? communicationRepo.findByTenantIdAndCustomerIdOrderByOccurredAtDesc(target.tenant().getId(), target.customer().getId())
                : communicationRepo.findByTenantIdAndLeadIdOrderByOccurredAtDesc(target.tenant().getId(), target.lead().getId());
        return rows.stream().map(this::toDto).toList();
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
        } catch (MailException | MessagingException e) {
            log.warn("Customer email {} failed: {}", email.getId(), e.getMessage());
            email.setStatus(CommunicationStatus.FAILED);
            email.setErrorMessage(truncate("Email could not be sent: " + e.getMessage()));
        }
        return toDto(communicationRepo.save(email));
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
     * Sends one SMS on the system's behalf (FR-5.3 alerts) and logs it with its outcome. Never throws:
     * an unconfigured provider or a missing number is logged as FAILED so admins can see why.
     */
    @Transactional
    public void sendAutomatedSms(Tenant tenant, Customer customer, String rawPhone, String body) {
        Communication sms = newLog(new Target(tenant, customer, null), CommunicationChannel.SMS, CommunicationDirection.OUTBOUND, null);
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
        communicationRepo.save(sms);
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
                .or(() -> communicationRepo.findFirstByChannelAndDirectionAndCounterpartOrderByOccurredAtDesc(
                        message.channel(), CommunicationDirection.OUTBOUND, from).map(this::targetOf))
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
        return Optional.of(communicationRepo.save(inbound));
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
        } catch (TwilioClient.TwilioException e) {
            row.setStatus(CommunicationStatus.FAILED);
            row.setErrorMessage(truncate(e.getMessage()));
        }
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
