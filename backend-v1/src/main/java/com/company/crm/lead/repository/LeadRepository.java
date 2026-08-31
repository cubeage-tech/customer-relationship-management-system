package com.company.crm.lead.repository;

import com.company.crm.lead.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByTenantId(Long tenantId);

    List<Lead> findByTenantIdAndOwnerId(Long tenantId, Long ownerId);

    List<Lead> findByTenantIdAndCampaignId(Long tenantId, Long campaignId);

    List<Lead> findByTenantIdAndCampaignIdIsNotNull(Long tenantId);
}
