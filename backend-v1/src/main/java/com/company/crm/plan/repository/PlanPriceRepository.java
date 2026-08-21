package com.company.crm.plan.repository;

import com.company.crm.common.enums.SubscriptionPlan;
import com.company.crm.plan.entity.PlanPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanPriceRepository extends JpaRepository<PlanPrice, Long> {
    Optional<PlanPrice> findByPlan(SubscriptionPlan plan);
}
