package com.company.crm.salesforce.repository;

import com.company.crm.salesforce.entity.SalesActivity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalesActivityRepository extends JpaRepository<SalesActivity, Long> {

    @EntityGraph(attributePaths = {"user", "customer", "lead"})
    List<SalesActivity> findByTenantIdAndUserIdInAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
            Long tenantId, Collection<Long> userIds, LocalDateTime from, LocalDateTime to);

    /** FR-M.6: a visit the app already synced. */
    Optional<SalesActivity> findByUserIdAndClientRef(Long userId, String clientRef);
}
