package com.company.crm.salesforce.repository;

import com.company.crm.salesforce.entity.SalesTarget;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalesTargetRepository extends JpaRepository<SalesTarget, Long> {

    @EntityGraph(attributePaths = {"user", "team"})
    List<SalesTarget> findByTenantIdOrderByPeriodStartDescIdDesc(Long tenantId);

    @EntityGraph(attributePaths = {"user", "team"})
    Optional<SalesTarget> findByIdAndTenantId(Long id, Long tenantId);
}
