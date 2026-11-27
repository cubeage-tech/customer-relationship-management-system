package com.company.crm.salesforce.repository;

import com.company.crm.salesforce.entity.CommissionEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommissionEntryRepository extends JpaRepository<CommissionEntry, Long> {

    Optional<CommissionEntry> findByOpportunityId(Long opportunityId);

    @EntityGraph(attributePaths = {"user", "opportunity", "opportunity.customer"})
    List<CommissionEntry> findByTenantIdOrderByWonAtDesc(Long tenantId);

    @EntityGraph(attributePaths = {"user", "opportunity", "opportunity.customer"})
    Optional<CommissionEntry> findByIdAndTenantId(Long id, Long tenantId);
}
