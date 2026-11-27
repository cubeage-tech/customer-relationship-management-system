package com.company.crm.campaign.service;

import com.company.crm.campaign.entity.Campaign;
import com.company.crm.campaign.repository.CampaignRepository;
import com.company.crm.common.enums.CampaignStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FR-7.1b: time-based campaign transitions, each in its own transaction — scheduled campaigns
 * launch at their time (sending, for email/SMS), and running campaigns complete once their end
 * date has passed.
 */
@Service
@RequiredArgsConstructor
public class CampaignScheduleService {

    private final CampaignRepository campaignRepository;
    private final CampaignDeliveryService deliveryService;

    @Transactional(readOnly = true)
    public List<Long> dueToLaunch() {
        return campaignRepository.findDueScheduledIds(CampaignStatus.SCHEDULED, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Long> dueToComplete() {
        return campaignRepository.findEndedIds(CampaignStatus.ACTIVE, LocalDate.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void launchScheduled(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign != null && campaign.getStatus() == CampaignStatus.SCHEDULED) {
            deliveryService.launch(campaign);
        }
    }

    /** Completed campaigns stop sending; anything still queued is not sent. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign != null && campaign.getStatus() == CampaignStatus.ACTIVE) {
            campaign.setStatus(CampaignStatus.COMPLETED);
            campaignRepository.save(campaign);
            deliveryService.cancelQueued(campaign);
        }
    }
}
