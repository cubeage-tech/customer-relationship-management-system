package com.company.crm.salesforce.repository;

import com.company.crm.salesforce.entity.CommissionRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, Long> {

    /** Evaluation order: lowest priority first, then oldest. */
    @EntityGraph(attributePaths = {"user", "team"})
    List<CommissionRule> findByTenantIdOrderByPriorityAscIdAsc(Long tenantId);

    @EntityGraph(attributePaths = {"user", "team"})
    Optional<CommissionRule> findByIdAndTenantId(Long id, Long tenantId);
}
