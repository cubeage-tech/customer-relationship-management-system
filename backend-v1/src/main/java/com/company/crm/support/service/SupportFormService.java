package com.company.crm.support.service;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.TicketPriority;
import com.company.crm.common.exception.ApiException;
import com.company.crm.communication.repository.CommunicationMatchQuery;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.support.dto.request.SupportRequestReqDto;
import com.company.crm.support.entity.CustomerAsset;
import com.company.crm.support.entity.ServiceTicket;
import com.company.crm.support.repository.CustomerAssetRepository;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * FR-6.1: customers raise service requests themselves through the tenant's public "Report an
 * issue" form (no login; the link's token identifies the tenant). The email they give must be the
 * customer's or one of its contacts' addresses on record — the request becomes an open ticket on
 * that customer, with the usual SLA and auto-assignment, and they get a confirmation email.
 */
@Service
@RequiredArgsConstructor
public class SupportFormService {

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final TenantRepository tenantRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAssetRepository assetRepository;
    private final CommunicationMatchQuery matchQuery;
    private final ServiceTicketService ticketService;
    private final TicketCustomerMessenger messenger;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /** The admin's shareable form link (created on first use). */
    @Transactional
    public String link(User admin) {
        Tenant tenant = findTenant(admin);
        if (tenant.getSupportFormToken() == null) {
            tenant.setSupportFormToken(newToken());
            tenantRepository.save(tenant);
        }
        return url(tenant);
    }

    /** Replaces the link — the old one stops working (e.g. if it was abused). */
    @Transactional
    public String resetLink(User admin) {
        Tenant tenant = findTenant(admin);
        tenant.setSupportFormToken(newToken());
        tenantRepository.save(tenant);
        return url(tenant);
    }

    /** What the public form shows: the company's name. */
    @Transactional(readOnly = true)
    public Map<String, String> formInfo(String token) {
        return Map.of("companyName", activeTenant(token).getCompanyName());
    }

    @Transactional
    public Map<String, Object> submit(String token, SupportRequestReqDto dto) {
        Tenant tenant = activeTenant(token);
        String email = dto.getEmail().trim().toLowerCase();
        List<Long> customerIds = matchQuery.byEmail(email).stream()
                .filter(m -> tenant.getId().equals(m.tenantId()) && m.customerId() != null)
                .map(CommunicationMatchQuery.Match::customerId)
                .distinct()
                .toList();
        if (customerIds.size() != 1) {
            throw ApiException.badRequest("We couldn't match this email to a customer account. Please use the address you "
                    + "usually correspond with " + tenant.getCompanyName() + " from, or contact us directly.");
        }
        Customer customer = customerRepository.findByIdAndTenantId(customerIds.get(0), tenant.getId()).orElseThrow();

        TicketPriority priority;
        try {
            priority = TicketPriority.fromDbValue(dto.getPriority());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Choose a priority");
        }
        CustomerAsset asset = dto.getSerialNumber() == null || dto.getSerialNumber().isBlank() ? null
                : assetRepository.findByTenantIdAndCustomerIdOrderByNameAsc(tenant.getId(), customer.getId()).stream()
                        .filter(a -> a.getSerialNumber() != null && a.getSerialNumber().equalsIgnoreCase(dto.getSerialNumber().trim()))
                        .findFirst().orElse(null);

        ServiceTicket ticket = ticketService.createFromSupportForm(customer, email, dto.getSubject().trim(),
                dto.getDescription(), priority, asset);
        messenger.confirmSupportRequest(ticket);
        return Map.of("ticketId", ticket.getId(), "companyName", tenant.getCompanyName());
    }

    // ==================== Helpers ====================

    private Tenant activeTenant(String token) {
        Tenant tenant = token == null || token.isBlank() ? null : tenantRepository.findBySupportFormToken(token).orElse(null);
        if (tenant == null || tenant.getStatus() != AccountStatus.ACTIVE) {
            throw ApiException.notFound("This support link is not valid");
        }
        return tenant;
    }

    private Tenant findTenant(User admin) {
        if (admin.getTenant() == null) {
            throw ApiException.forbidden("The support form is configured per tenant");
        }
        return tenantRepository.findById(admin.getTenant().getId()).orElseThrow();
    }

    private String url(Tenant tenant) {
        return frontendUrl + "/support/" + tenant.getSupportFormToken();
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
