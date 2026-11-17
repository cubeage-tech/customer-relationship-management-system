package com.company.crm.communication.service;

import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.common.enums.CommunicationDirection;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.communication.dto.request.CommunicationLogReqDto;
import com.company.crm.communication.dto.response.CommunicationLogResDto;
import com.company.crm.communication.entity.CommunicationLog;
import com.company.crm.communication.repository.CommunicationLogRepository;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CommunicationService {

    private static final String DEFAULT_STATUS = "logged";
    private static final List<String> ALLOWED_STATUSES =
            List.of("logged", "sent", "delivered", "received", "failed", "missed");

    private final CommunicationLogRepository communicationLogRepository;
    private final CustomerRepository customerRepository;
    private final DataScopeResolver dataScopeResolver;

    @Transactional(readOnly = true)
    public List<CommunicationLogResDto> getCustomerTimeline(User currentUser, Long customerId) {
        Long tenantId = requireTenantId(currentUser);
        Customer customer = findCustomer(customerId, tenantId);
        assertCanViewCustomer(currentUser, customer);
        return communicationLogRepository
                .findByTenantIdAndCustomerIdOrderByOccurredAtDescIdDesc(tenantId, customerId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CommunicationLogResDto createLog(User currentUser, Long customerId, CommunicationLogReqDto dto) {
        Long tenantId = requireTenantId(currentUser);
        Customer customer = findCustomer(customerId, tenantId);
        assertCanViewCustomer(currentUser, customer);

        CommunicationLog log = new CommunicationLog();
        log.setTenant(customer.getTenant());
        log.setCustomer(customer);
        log.setCreatedBy(currentUser);
        log.setChannel(parseChannel(dto.getChannel()));
        log.setDirection(parseDirection(dto.getDirection()));
        log.setSubject(clean(dto.getSubject()));
        log.setContent(clean(dto.getContent()));
        log.setContactName(clean(dto.getContactName()));
        log.setContactAddress(clean(dto.getContactAddress()));
        log.setStatus(parseStatus(dto.getStatus()));
        log.setOccurredAt(dto.getOccurredAt());
        return toDto(communicationLogRepository.save(log));
    }

    private CommunicationLogResDto toDto(CommunicationLog log) {
        return new CommunicationLogResDto(
                log.getId(),
                log.getChannel().getDbValue(),
                log.getDirection().getDbValue(),
                log.getSubject(),
                log.getContent(),
                log.getContactName(),
                log.getContactAddress(),
                log.getStatus(),
                log.getOccurredAt(),
                log.getCreatedAt(),
                log.getCreatedBy().getId(),
                log.getCreatedBy().getFullName());
    }

    private Customer findCustomer(Long customerId, Long tenantId) {
        return customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
    }

    private void assertCanViewCustomer(User currentUser, Customer customer) {
        dataScopeResolver.assertCanAccess(
                currentUser,
                customer.getOwner() == null ? null : customer.getOwner().getId(),
                "customer");
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Communication history is scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }

    private CommunicationChannel parseChannel(String value) {
        try {
            return CommunicationChannel.fromDbValue(value.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Channel must be email, whatsapp, sms or call");
        }
    }

    private CommunicationDirection parseDirection(String value) {
        try {
            return CommunicationDirection.fromDbValue(value.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Direction must be inbound or outbound");
        }
    }

    private String parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_STATUS;
        }
        String status = value.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(status)) {
            throw ApiException.badRequest("Status must be logged, sent, delivered, received, failed or missed");
        }
        return status;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
