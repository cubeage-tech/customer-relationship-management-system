package com.company.crm.dashboard.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.company.crm.dashboard.dto.response.SuperAdminTenantResDto;
import com.company.crm.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuperAdminTenantService {
    
    private final TenantRepository tenantRepository;

    public List<SuperAdminTenantResDto> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(tenant -> new SuperAdminTenantResDto(
                        tenant.getId(),
                        tenant.getCompanyName(),
                        tenant.getLegalName(),
                        tenant.getPlan().getDbValue(),
                        tenant.getStatus().getDbValue(),
                        tenant.getCreatedAt(),
                        tenant.getUpdatedAt()))
                .toList();
    }

}
