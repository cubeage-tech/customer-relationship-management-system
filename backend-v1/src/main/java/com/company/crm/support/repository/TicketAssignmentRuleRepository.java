package com.company.crm.support.repository;

import com.company.crm.support.entity.TicketAssignmentRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketAssignmentRuleRepository extends JpaRepository<TicketAssignmentRule, Long> {

    /** Evaluation order: lowest priority first, then oldest. */
    @EntityGraph(attributePaths = "assignUser")
    List<TicketAssignmentRule> findByTenantIdOrderByPriorityAscIdAsc(Long tenantId);

    @EntityGraph(attributePaths = "assignUser")
    Optional<TicketAssignmentRule> findByIdAndTenantId(Long id, Long tenantId);
}
