package com.company.crm.sales.repository;

import com.company.crm.sales.entity.Opportunity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {

    List<Opportunity> findByTenantId(Long tenantId);

    List<Opportunity> findByTenantIdAndOwnerId(Long tenantId, Long ownerId);
}
