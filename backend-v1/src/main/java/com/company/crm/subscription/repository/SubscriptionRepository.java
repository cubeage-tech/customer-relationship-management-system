package com.company.crm.subscription.repository;

import com.company.crm.common.enums.SubscriptionStatus;
import com.company.crm.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    // plan is always needed alongside the subscription (limits, DTOs) and open-in-view is off.
    @EntityGraph(attributePaths = {"plan", "tenant"})
    Optional<Subscription> findByTenantId(Long tenantId);

    List<Subscription> findByStatusInAndExpiresAtBefore(Collection<SubscriptionStatus> statuses, LocalDateTime cutoff);
}
