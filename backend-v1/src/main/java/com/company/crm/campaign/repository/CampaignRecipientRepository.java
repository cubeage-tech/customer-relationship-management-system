package com.company.crm.campaign.repository;

import com.company.crm.campaign.entity.CampaignRecipient;
import com.company.crm.common.enums.CampaignStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, Long> {

    @EntityGraph(attributePaths = "customer")
    List<CampaignRecipient> findByCampaignIdOrderByIdAsc(Long campaignId);

    @EntityGraph(attributePaths = {"campaign", "customer"})
    Optional<CampaignRecipient> findByTrackingToken(String trackingToken);

    /** FR-7.1c: the next recipients waiting to be sent, oldest first — only for campaigns that are running. */
    @Query("select r.id from CampaignRecipient r where r.status = 'queued' and r.campaign.status = :running order by r.id")
    List<Long> findQueuedIds(@Param("running") CampaignStatus running, Pageable page);

    /** FR-7.1c: claims a queued recipient for sending (0 = already taken by another run). */
    @Modifying
    @Query("update CampaignRecipient r set r.status = 'sent', r.sentAt = :now where r.id = :id and r.status = 'queued'")
    int claimForSend(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** FR-7.4: per-campaign totals — [queued, sent, failed, skipped, opened, clicked]. */
    @Query("select coalesce(sum(case when r.status = 'queued' then 1 else 0 end), 0),"
            + " coalesce(sum(case when r.status = 'sent' then 1 else 0 end), 0),"
            + " coalesce(sum(case when r.status = 'failed' then 1 else 0 end), 0),"
            + " coalesce(sum(case when r.status = 'skipped' then 1 else 0 end), 0),"
            + " count(r.openedAt), count(r.clickedAt)"
            + " from CampaignRecipient r where r.campaign.id = :campaignId")
    List<Object[]> totals(@Param("campaignId") Long campaignId);

    /**
     * FR-7.4: recipients whose customer won an opportunity or approved a quotation within
     * {@code days} after the campaign reached them.
     */
    @Query(value = "SELECT count(*) FROM campaign_recipients r WHERE r.campaign_id = :campaignId AND r.status = 'sent'"
            + " AND (EXISTS (SELECT 1 FROM opportunities o WHERE o.customer_id = r.customer_id AND o.stage = 'won'"
            + "       AND o.stage_changed_at >= r.sent_at AND o.stage_changed_at < r.sent_at + make_interval(days => :days))"
            + "  OR EXISTS (SELECT 1 FROM quotations q WHERE q.customer_id = r.customer_id AND q.status = 'approved'"
            + "       AND q.customer_approved_at >= r.sent_at AND q.customer_approved_at < r.sent_at + make_interval(days => :days)))",
            nativeQuery = true)
    long countConverted(@Param("campaignId") Long campaignId, @Param("days") int days);
}
