package com.company.crm.lead.repository;

import com.company.crm.lead.entity.LeadAssignmentRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeadAssignmentRuleRepository extends JpaRepository<LeadAssignmentRule, Long> {

    /** Evaluation order: lowest priority first, then oldest. */
    @EntityGraph(attributePaths = {"assignUser", "assignTeam"})
    List<LeadAssignmentRule> findByTenantIdOrderByPriorityAscIdAsc(Long tenantId);

    @EntityGraph(attributePaths = {"assignUser", "assignTeam"})
    Optional<LeadAssignmentRule> findByIdAndTenantId(Long id, Long tenantId);
}
