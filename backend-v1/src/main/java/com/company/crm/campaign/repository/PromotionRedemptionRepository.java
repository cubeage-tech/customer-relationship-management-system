package com.company.crm.campaign.repository;

import com.company.crm.campaign.entity.PromotionRedemption;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, Long> {

    @EntityGraph(attributePaths = {"customer", "quotation", "recordedBy", "promotion"})
    List<PromotionRedemption> findByPromotionIdOrderByRedeemedAtDesc(Long promotionId);

    @EntityGraph(attributePaths = {"customer", "quotation", "recordedBy", "promotion"})
    List<PromotionRedemption> findByTenantIdAndCustomerIdOrderByRedeemedAtDesc(Long tenantId, Long customerId);

    long countByPromotionId(Long promotionId);

    boolean existsByPromotionIdAndCustomerId(Long promotionId, Long customerId);
}
