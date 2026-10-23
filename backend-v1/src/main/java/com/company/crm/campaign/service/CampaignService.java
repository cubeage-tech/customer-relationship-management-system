package com.company.crm.campaign.service;

import com.company.crm.campaign.dto.request.CampaignReqDto;
import com.company.crm.campaign.dto.request.CampaignStatusReqDto;
import com.company.crm.campaign.dto.response.CampaignResDto;
import com.company.crm.campaign.dto.response.CampaignMetricsDto;
import com.company.crm.campaign.dto.response.CampaignSummaryDto;
import com.company.crm.campaign.entity.Campaign;
import com.company.crm.campaign.entity.CustomerSegment;
import com.company.crm.campaign.repository.CampaignRecipientRepository;
import com.company.crm.campaign.mapper.CampaignMapper;
import com.company.crm.campaign.repository.CampaignRepository;
import com.company.crm.common.enums.CampaignChannel;
import com.company.crm.common.enums.CampaignStatus;
import com.company.crm.common.enums.LeadStage;
import com.company.crm.common.exception.ApiException;
import com.company.crm.lead.dto.response.LeadResDto;
import com.company.crm.lead.entity.Lead;
import com.company.crm.lead.mapper.LeadMapper;
import com.company.crm.lead.repository.LeadRepository;
import com.company.crm.subscription.service.PlanLimitService;
import com.company.crm.subscription.service.PlanLimitService.LimitedResource;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final CampaignMapper campaignMapper;
    private final LeadMapper leadMapper;
    private final PlanLimitService planLimitService;
    private final SegmentService segmentService;
    private final CampaignDeliveryService deliveryService;
    private final CampaignRecipientRepository recipientRepository;

    /** FR-7.4: a recipient counts as converted if they win/approve within this many days of the send. */
    private static final int CONVERSION_WINDOW_DAYS = 30;

    // open-in-view is disabled (see application.properties) — the mapper walks lazy
    // associations (owner, tenant), so the session must stay open through mapping.
    @Transactional(readOnly = true)
    public List<CampaignResDto> listCampaigns(User currentUser, String status, String channel, String search) {
        return campaignRepository.findByTenantId(requireTenantId(currentUser)).stream()
                .filter(c -> status == null || status.isBlank() || c.getStatus().getDbValue().equals(status))
                .filter(c -> channel == null || channel.isBlank() || c.getChannel().getDbValue().equals(channel))
                .filter(c -> search == null || search.isBlank()
                        || c.getName().toLowerCase().contains(search.trim().toLowerCase()))
                .map(this::toDtoWithLeadStats)
                .toList();
    }

    @Transactional(readOnly = true)
    public CampaignResDto getCampaign(User currentUser, Long campaignId) {
        return toDtoWithLeadStats(findCampaign(currentUser, campaignId));
    }

    /** FR-5.x: tenant-wide campaign performance, for the marketing dashboard. */
    @Transactional(readOnly = true)
    public CampaignSummaryDto getSummary(User currentUser) {
        Long tenantId = requireTenantId(currentUser);
        long activeCampaigns = campaignRepository.findByTenantId(tenantId).stream()
                .filter(c -> c.getStatus() == CampaignStatus.ACTIVE)
                .count();

        List<Lead> campaignLeads = leadRepository.findByTenantIdAndCampaignIdIsNotNull(tenantId);
        long leadsGenerated = campaignLeads.size();
        long qualifiedLeads = campaignLeads.stream().filter(l -> l.getStage() != LeadStage.NEW_LEAD).count();
        long convertedLeads = campaignLeads.stream().filter(l -> l.getStage() == LeadStage.CONVERTED).count();
        double conversionRate = leadsGenerated == 0 ? 0.0 : (convertedLeads * 100.0) / leadsGenerated;

        return new CampaignSummaryDto(activeCampaigns, leadsGenerated, qualifiedLeads, conversionRate);
    }

    /** FR-5.3: the leads this campaign has generated. */
    @Transactional(readOnly = true)
    public List<LeadResDto> listCampaignLeads(User currentUser, Long campaignId) {
        Campaign campaign = findCampaign(currentUser, campaignId);
        return leadRepository.findByTenantIdAndCampaignId(requireTenantId(currentUser), campaign.getId()).stream()
                .map(leadMapper::toDto)
                .toList();
    }

    @Transactional
    public CampaignResDto createCampaign(User currentUser, CampaignReqDto dto) {
        requireTenantId(currentUser);
        planLimitService.assertCanAdd(currentUser.getTenant(), LimitedResource.CAMPAIGNS);

        Campaign campaign = new Campaign();
        campaign.setTenant(currentUser.getTenant());
        campaign.setCreatedBy(currentUser);
        campaign.setStatus(CampaignStatus.DRAFT);
        applyFields(currentUser, campaign, dto);
        return toDtoWithLeadStats(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResDto updateCampaign(User currentUser, Long campaignId, CampaignReqDto dto) {
        Campaign campaign = findCampaign(currentUser, campaignId);
        applyFields(currentUser, campaign, dto);
        return toDtoWithLeadStats(campaignRepository.save(campaign));
    }

    @Transactional
    public void deleteCampaign(User currentUser, Long campaignId) {
        campaignRepository.delete(findCampaign(currentUser, campaignId));
    }

    /**
     * Status changes. Activating a draft/scheduled campaign is a launch (FR-7.1c: email/SMS send);
     * scheduling goes through {@link #schedule}; cancelling or completing stops unsent messages.
     */
    @Transactional
    public CampaignResDto changeStatus(User currentUser, Long campaignId, CampaignStatusReqDto dto) {
        Campaign campaign = findCampaign(currentUser, campaignId);
        CampaignStatus target = parseStatus(dto.getStatus());
        if (target == CampaignStatus.SCHEDULED) {
            throw ApiException.badRequest("Choose a date and time to schedule the campaign");
        }
        if (target == CampaignStatus.ACTIVE
                && (campaign.getStatus() == CampaignStatus.DRAFT || campaign.getStatus() == CampaignStatus.SCHEDULED)) {
            deliveryService.launch(campaign);
            return toDtoWithLeadStats(campaign);
        }
        campaign.setStatus(target);
        campaign.setScheduledAt(null);
        Campaign saved = campaignRepository.save(campaign);
        if (target == CampaignStatus.CANCELLED || target == CampaignStatus.COMPLETED) {
            deliveryService.cancelQueued(saved);
        }
        return toDtoWithLeadStats(saved);
    }

    /** FR-7.1c: launch now — email/SMS campaigns with a segment send to it. */
    @Transactional
    public CampaignResDto launch(User currentUser, Long campaignId) {
        Campaign campaign = findCampaign(currentUser, campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw ApiException.badRequest("Only a draft or scheduled campaign can be launched");
        }
        deliveryService.launch(campaign);
        return toDtoWithLeadStats(campaign);
    }

    /** FR-7.1b: launch automatically at {@code scheduledAt} (a draft, or re-scheduling a scheduled one). */
    @Transactional
    public CampaignResDto schedule(User currentUser, Long campaignId, LocalDateTime scheduledAt) {
        Campaign campaign = findCampaign(currentUser, campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw ApiException.badRequest("Only a draft campaign can be scheduled");
        }
        if (scheduledAt == null || !scheduledAt.isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("Pick a time in the future");
        }
        CampaignDeliveryService.assertReadyToSend(campaign);
        campaign.setStatus(CampaignStatus.SCHEDULED);
        campaign.setScheduledAt(scheduledAt);
        return toDtoWithLeadStats(campaignRepository.save(campaign));
    }

    /** FR-7.4: sent / opened / clicked / converted, plus the campaign's lead figures. */
    @Transactional(readOnly = true)
    public CampaignMetricsDto metrics(User currentUser, Long campaignId) {
        Campaign campaign = findCampaign(currentUser, campaignId);
        Object[] t = recipientRepository.totals(campaign.getId()).get(0);
        long queued = num(t[0]);
        long sent = num(t[1]);
        long failed = num(t[2]);
        long skipped = num(t[3]);
        long opened = num(t[4]);
        long clicked = num(t[5]);
        long converted = recipientRepository.countConverted(campaign.getId(), CONVERSION_WINDOW_DAYS);
        List<Lead> leads = leadRepository.findByTenantIdAndCampaignId(campaign.getTenant().getId(), campaign.getId());
        long convertedLeads = leads.stream().filter(l -> l.getStage() == LeadStage.CONVERTED).count();
        return new CampaignMetricsDto(queued + sent + failed + skipped, queued, sent, failed, skipped, opened, clicked, converted,
                rate(opened, sent), rate(clicked, sent), rate(converted, sent), leads.size(), convertedLeads);
    }

    /** FR-7.4: who the campaign went to, with each delivery's status and tracking. */
    @Transactional(readOnly = true)
    public List<CampaignMetricsDto.Recipient> recipients(User currentUser, Long campaignId) {
        Campaign campaign = findCampaign(currentUser, campaignId);
        return recipientRepository.findByCampaignIdOrderByIdAsc(campaign.getId()).stream()
                .map(r -> new CampaignMetricsDto.Recipient(r.getCustomer().getId(), r.getCustomer().getCompanyName(), r.getAddress(),
                        r.getStatus(), r.getErrorMessage(), r.getSentAt(), r.getOpenedAt(), r.getClickedAt()))
                .toList();
    }

    // ==================== Helpers ====================

    private CampaignResDto toDtoWithLeadStats(Campaign campaign) {
        List<Lead> leads = leadRepository.findByTenantIdAndCampaignId(campaign.getTenant().getId(), campaign.getId());
        long convertedLeads = leads.stream().filter(l -> l.getStage() == LeadStage.CONVERTED).count();
        return campaignMapper.toDto(campaign, leads.size(), convertedLeads);
    }

    private void applyFields(User currentUser, Campaign campaign, CampaignReqDto dto) {
        campaign.setName(dto.getName());
        campaign.setDescription(dto.getDescription());
        campaign.setChannel(parseChannel(dto.getChannel()));
        campaign.setStartDate(dto.getStartDate());
        campaign.setEndDate(dto.getEndDate());
        campaign.setBudget(dto.getBudget() != null ? dto.getBudget() : BigDecimal.ZERO);
        campaign.setActualCost(dto.getActualCost() != null ? dto.getActualCost() : BigDecimal.ZERO);
        campaign.setOwner(dto.getOwnerId() != null ? resolveOwner(currentUser, dto.getOwnerId()) : currentUser);

        // FR-7.1c: what an email/SMS campaign sends, and to whom — fixed once it has been sent.
        CustomerSegment segment = dto.getSegmentId() != null ? segmentService.requireSegment(currentUser, dto.getSegmentId()) : null;
        String subject = blankToNull(dto.getMessageSubject());
        String body = blankToNull(dto.getMessageBody());
        boolean sent = campaign.getLaunchedAt() != null && campaign.getSegment() != null;
        if (sent && (segment == null || !segment.getId().equals(campaign.getSegment().getId())
                || !Objects.equals(subject, campaign.getMessageSubject()) || !Objects.equals(body, campaign.getMessageBody()))) {
            throw ApiException.badRequest("This campaign has already been sent, so its audience and message are fixed");
        }
        campaign.setSegment(segment);
        campaign.setMessageSubject(subject);
        campaign.setMessageBody(body);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long num(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static double rate(long part, long whole) {
        return whole == 0 ? 0.0 : Math.round(part * 1000.0 / whole) / 10.0;
    }

    private User resolveOwner(User currentUser, Long ownerId) {
        User owner = userRepository.findByIdAndTenantId(ownerId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Owner must belong to your tenant"));
        return owner;
    }

    private CampaignChannel parseChannel(String rawChannel) {
        try {
            return CampaignChannel.fromDbValue(rawChannel);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown campaign channel: " + rawChannel);
        }
    }

    private CampaignStatus parseStatus(String rawStatus) {
        try {
            return CampaignStatus.fromDbValue(rawStatus);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown campaign status: " + rawStatus);
        }
    }

    private Campaign findCampaign(User currentUser, Long campaignId) {
        Campaign campaign = campaignRepository.findByIdAndTenantId(campaignId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Campaign not found"));
        return campaign;
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Campaigns are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
