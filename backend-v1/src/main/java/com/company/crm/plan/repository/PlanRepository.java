package com.company.crm.plan.repository;

import com.company.crm.plan.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByCode(String code);

    Optional<Plan> findByCodeAndActiveTrue(String code);

    List<Plan> findByActiveTrueOrderByDisplayOrderAsc();

    boolean existsByActiveTrueAndDisplayOrderGreaterThan(Integer displayOrder);
}
