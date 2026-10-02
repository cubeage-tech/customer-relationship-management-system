package com.company.crm.lead.repository;

import com.company.crm.lead.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    /** Tenant-scoped lookup: a record from another tenant is indistinguishable from a missing one. */
    Optional<Lead> findByIdAndTenantId(Long id, Long tenantId);

    List<Lead> findByTenantId(Long tenantId);

    List<Lead> findByTenantIdAndOwnerId(Long tenantId, Long ownerId);

    List<Lead> findByTenantIdAndOwnerIdIn(Long tenantId, Collection<Long> ownerIds);

    List<Lead> findByTenantIdAndCampaignId(Long tenantId, Long campaignId);

    List<Lead> findByTenantIdAndCampaignIdIsNotNull(Long tenantId);

    long countByTenantId(Long tenantId);
}
