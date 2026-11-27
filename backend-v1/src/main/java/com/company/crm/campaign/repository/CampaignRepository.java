package com.company.crm.campaign.repository;

import com.company.crm.campaign.entity.Campaign;
import com.company.crm.common.enums.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /** Tenant-scoped lookup: a record from another tenant is indistinguishable from a missing one. */
    Optional<Campaign> findByIdAndTenantId(Long id, Long tenantId);

    List<Campaign> findByTenantId(Long tenantId);

    long countByTenantId(Long tenantId);

    /** FR-7.1b: scheduled campaigns whose launch time has come. */
    @Query("select c.id from Campaign c where c.status = :scheduled and c.scheduledAt <= :now order by c.scheduledAt")
    List<Long> findDueScheduledIds(@Param("scheduled") CampaignStatus scheduled, @Param("now") LocalDateTime now);

    /** FR-7.1b: running campaigns whose end date has passed. */
    @Query("select c.id from Campaign c where c.status = :active and c.endDate < :today")
    List<Long> findEndedIds(@Param("active") CampaignStatus active, @Param("today") LocalDate today);
}
