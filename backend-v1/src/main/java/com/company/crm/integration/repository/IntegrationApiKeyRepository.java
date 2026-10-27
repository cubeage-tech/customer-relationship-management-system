package com.company.crm.integration.repository;

import com.company.crm.integration.entity.IntegrationApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationApiKeyRepository extends JpaRepository<IntegrationApiKey, Long> {

    Optional<IntegrationApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);

    List<IntegrationApiKey> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<IntegrationApiKey> findByIdAndTenantId(Long id, Long tenantId);
}
