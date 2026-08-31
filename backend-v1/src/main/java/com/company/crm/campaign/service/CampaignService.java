package com.company.crm.campaign.service;

import com.company.crm.campaign.dto.request.CampaignReqDto;
import com.company.crm.campaign.dto.request.CampaignStatusReqDto;
import com.company.crm.campaign.dto.response.CampaignResDto;
import com.company.crm.campaign.dto.response.CampaignSummaryDto;
import com.company.crm.campaign.entity.Campaign;
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
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final CampaignMapper campaignMapper;
    private final LeadMapper leadMapper;

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

    @Transactional
    public CampaignResDto changeStatus(User currentUser, Long campaignId, CampaignStatusReqDto dto) {
        Campaign campaign = findCampaign(currentUser, campaignId);
        campaign.setStatus(parseStatus(dto.getStatus()));
        return toDtoWithLeadStats(campaignRepository.save(campaign));
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
    }

    private User resolveOwner(User currentUser, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.badRequest("Owner not found"));
        if (owner.getTenant() == null || !owner.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.badRequest("Owner must belong to your tenant");
        }
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
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> ApiException.notFound("Campaign not found"));
        if (!campaign.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.notFound("Campaign not found");
        }
        return campaign;
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Campaigns are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
