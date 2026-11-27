package com.company.crm.campaign.repository;

import com.company.crm.campaign.entity.Promotion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    @EntityGraph(attributePaths = "campaign")
    List<Promotion> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    @EntityGraph(attributePaths = "campaign")
    Optional<Promotion> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(Long tenantId, String code);

    boolean existsByTenantIdAndCodeIgnoreCaseAndIdNot(Long tenantId, String code, Long id);
}
