package com.company.crm.campaign.repository;

import com.company.crm.campaign.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /** Tenant-scoped lookup: a record from another tenant is indistinguishable from a missing one. */
    Optional<Campaign> findByIdAndTenantId(Long id, Long tenantId);

    List<Campaign> findByTenantId(Long tenantId);

    long countByTenantId(Long tenantId);
}
