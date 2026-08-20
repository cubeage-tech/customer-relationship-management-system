package com.company.crm.tenant.service;

import com.company.crm.tenant.dto.response.TenantResDto;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
