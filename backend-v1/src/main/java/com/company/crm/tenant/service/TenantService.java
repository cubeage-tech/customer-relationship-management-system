package com.company.crm.tenant.service;

import com.company.crm.tenant.dto.response.TenantResDto;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    public List<TenantResDto> listTenants() {
        return tenantRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TenantResDto deactivateTenant(Long tenantId) {
        Tenant tenant = findTenant(tenantId);
        tenant.setStatus(AccountStatus.DEACTIVATED);
        return toDto(tenantRepository.save(tenant));
    }

    @Transactional
    public TenantResDto restoreTenant(Long tenantId) {
        Tenant tenant = findTenant(tenantId);
        tenant.setStatus(AccountStatus.ACTIVE);
        return toDto(tenantRepository.save(tenant));
    }

    private Tenant findTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> ApiException.notFound("Tenant not found"));
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
