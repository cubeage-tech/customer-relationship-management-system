package com.company.crm.tenant.service;

import com.company.crm.common.audit.AuditService;
import com.company.crm.common.audit.AuditAction;
import com.company.crm.tenant.dto.response.TenantResDto;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.exception.TenantNotFoundException;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final AuditService auditService;

    public List<TenantResDto> listTenants() {
        return tenantRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TenantResDto deactivateTenant(User actor, Long tenantId) {
        Tenant tenant = findTenant(tenantId);
        tenant.setStatus(AccountStatus.DEACTIVATED);
        auditService.record(actor, tenantId, AuditAction.TENANT_DEACTIVATED, "tenant", tenantId, "name=" + tenant.getCompanyName());
        return toDto(tenantRepository.save(tenant));
    }

    @Transactional
    public TenantResDto restoreTenant(User actor, Long tenantId) {
        Tenant tenant = findTenant(tenantId);
        tenant.setStatus(AccountStatus.ACTIVE);
        auditService.record(actor, tenantId, AuditAction.TENANT_RESTORED, "tenant", tenantId, "name=" + tenant.getCompanyName());
        return toDto(tenantRepository.save(tenant));
    }

    private Tenant findTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found"));
    }

    private TenantResDto toDto(Tenant tenant) {
        return new TenantResDto(
                tenant.getId(),
                tenant.getCompanyName(),
                tenant.getLegalName(),
                tenant.getPlan().getDbValue(),
                tenant.getStatus().getDbValue(),
                tenant.getCreatedAt()
        );
    }
}
