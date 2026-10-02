package com.company.crm.sales.repository;

import com.company.crm.common.enums.OpportunityStage;
import com.company.crm.sales.entity.Opportunity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {

    List<Opportunity> findByTenantId(Long tenantId);

    List<Opportunity> findByTenantIdAndOwnerId(Long tenantId, Long ownerId);

    long countByTenantIdAndStageNotIn(Long tenantId, Collection<OpportunityStage> stages);

    long countByTenantIdAndStageNotIn(Long tenantId, List<OpportunityStage> stages);

    List<Opportunity> findByTenantIdAndStageAndStageChangedAtGreaterThanEqualAndStageChangedAtLessThan(
        Long tenantId,
        OpportunityStage stage,
        LocalDateTime start,
        LocalDateTime end
    );
}
