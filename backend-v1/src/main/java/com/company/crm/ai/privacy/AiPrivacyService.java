package com.company.crm.ai.privacy;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.customer.service.CustomerService;
import com.company.crm.lead.entity.Lead;
import com.company.crm.lead.repository.LeadRepository;
import com.company.crm.lead.service.LeadService;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SEC-5/SEC-6: what may reach the AI provider. A tenant admin can switch AI off, or keep message
 * text (emails, WhatsApp, SMS) away from it; staff can exclude a customer or lead from AI entirely.
 * Every AI feature checks these before building a prompt, and {@link AiRedactor} strips contact and
 * identity details from whatever is sent.
 */
@Service
@RequiredArgsConstructor
public class AiPrivacyService {

    private final TenantRepository tenantRepository;
    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final CustomerService customerService;
    private final LeadService leadService;
    private final AuditService auditService;

    public record AiSettings(boolean aiEnabled, boolean messageContent) {}

    public record Exclusion(String recordType, Long recordId, boolean excluded) {}

    /** What each feature sends — shown to admins so the policy is visible, not just enforced. */
    public record FeatureData(String feature, String sends, String neverSends) {}

    public static final List<FeatureData> POLICY = List.of(
            new FeatureData("Ask the CRM (chat)", "Your question; the names, stages and amounts of the matching records",
                    "Contact details, notes, message text, excluded records"),
            new FeatureData("Draft a message", "Company and contact name, industry, deal product and value, subjects of the last 3 messages",
                    "Email addresses, phone numbers, message bodies"),
            new FeatureData("Draft a quotation", "Your product names and prices, the customer's industry, the deal's product and value, past purchases",
                    "Contact details, notes"),
            new FeatureData("Customer sentiment / AI alerts", "The text of the message being analysed (if message text is allowed)",
                    "Email addresses, phone numbers and ID numbers inside it (removed first)"),
            new FeatureData("Lead score / revenue forecast", "Nothing — calculated inside the CRM", "Everything"));

    @Transactional(readOnly = true)
    public AiSettings settings(User user) {
        Tenant t = tenant(user);
        return new AiSettings(t.isAiEnabled(), t.isAiMessageContent());
    }

    @Transactional
    public AiSettings updateSettings(User admin, AiSettings req) {
        Tenant t = tenant(admin);
        if (t.isAiEnabled() != req.aiEnabled() || t.isAiMessageContent() != req.messageContent()) {
            t.setAiEnabled(req.aiEnabled());
            t.setAiMessageContent(req.messageContent());
            tenantRepository.save(t);
            auditService.record(admin, AuditAction.AI_SETTINGS_CHANGED, "tenant", t.getId(),
                    "ai=" + (req.aiEnabled() ? "on" : "off") + ", message_text=" + (req.messageContent() ? "allowed" : "withheld"));
        }
        return new AiSettings(t.isAiEnabled(), t.isAiMessageContent());
    }

    /** 403 AI_DISABLED when the tenant switched AI off. */
    @Transactional(readOnly = true)
    public void requireEnabled(User user) {
        if (!tenant(user).isAiEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AI features are switched off for your organisation", "AI_DISABLED");
        }
    }

    @Transactional(readOnly = true)
    public boolean enabled(Long tenantId) {
        return tenantRepository.findById(tenantId).map(Tenant::isAiEnabled).orElse(false);
    }

    /** Whether message text may be sent (AI on, and message text allowed). */
    @Transactional(readOnly = true)
    public boolean messageContentAllowed(Long tenantId) {
        return tenantRepository.findById(tenantId).map(t -> t.isAiEnabled() && t.isAiMessageContent()).orElse(false);
    }

    public void requireNotExcluded(Customer customer) {
        if (customer != null && customer.isAiExcluded()) {
            throw excluded();
        }
    }

    public void requireNotExcluded(Lead lead) {
        if (lead != null && lead.isAiExcluded()) {
            throw excluded();
        }
    }

    @Transactional(readOnly = true)
    public void requireNotExcludedCustomer(Long customerId) {
        if (customerExcluded(customerId)) {
            throw excluded();
        }
    }

    @Transactional(readOnly = true)
    public void requireNotExcludedLead(Long leadId) {
        if (leadExcluded(leadId)) {
            throw excluded();
        }
    }

    /** 403 when message text must stay out of the AI provider (or AI is off). */
    @Transactional(readOnly = true)
    public void requireMessageContentAllowed(User user) {
        requireEnabled(user);
        if (!tenant(user).isAiMessageContent()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your organisation keeps message text away from the AI provider", "AI_MESSAGES_WITHHELD");
        }
    }

    @Transactional(readOnly = true)
    public boolean customerExcluded(Long customerId) {
        return customerId != null && customerRepository.findById(customerId).map(Customer::isAiExcluded).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean leadExcluded(Long leadId) {
        return leadId != null && leadRepository.findById(leadId).map(Lead::isAiExcluded).orElse(false);
    }

    @Transactional(readOnly = true)
    public Exclusion exclusion(User user, String type, Long id) {
        return new Exclusion(type, id, "lead".equals(type) ? lead(user, id).isAiExcluded() : customer(user, id).isAiExcluded());
    }

    /** SEC-6: exclude (or include again) a customer or lead — same access as viewing it; audited. */
    @Transactional
    public Exclusion setExcluded(User user, String type, Long id, boolean excluded) {
        if ("lead".equals(type)) {
            Lead l = lead(user, id);
            if (l.isAiExcluded() != excluded) {
                l.setAiExcluded(excluded);
                leadRepository.save(l);
                auditService.record(user, AuditAction.AI_EXCLUSION_CHANGED, "lead", id, excluded ? "excluded from AI" : "included in AI again");
            }
            return new Exclusion(type, id, excluded);
        }
        Customer c = customer(user, id);
        if (c.isAiExcluded() != excluded) {
            c.setAiExcluded(excluded);
            customerRepository.save(c);
            auditService.record(user, AuditAction.AI_EXCLUSION_CHANGED, "customer", id, excluded ? "excluded from AI" : "included in AI again");
        }
        return new Exclusion("customer", id, excluded);
    }

    private Customer customer(User user, Long id) {
        customerService.getCustomer(user, id);
        return customerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Customer not found"));
    }

    private Lead lead(User user, Long id) {
        leadService.getLead(user, id);
        return leadRepository.findById(id).orElseThrow(() -> ApiException.notFound("Lead not found"));
    }

    private Tenant tenant(User user) {
        if (user.getTenant() == null) {
            throw ApiException.forbidden("AI settings are per tenant");
        }
        return tenantRepository.findById(user.getTenant().getId()).orElseThrow();
    }

    private static ApiException excluded() {
        return new ApiException(HttpStatus.FORBIDDEN, "This record is excluded from AI features", "AI_EXCLUDED");
    }
}
