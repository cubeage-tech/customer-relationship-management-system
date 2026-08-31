package com.company.crm.campaign.repository;

import com.company.crm.campaign.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByTenantId(Long tenantId);
}
