package com.company.crm.lead.service;

import com.company.crm.lead.event.LeadChangedEvent;
import com.company.crm.common.pagination.PageRequestFactory;
import com.company.crm.common.pagination.PageResponse;
import com.company.crm.common.scope.ScopeSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.util.Map;
import com.company.crm.lead.repository.LeadSpecifications;
import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.audit.StageOverrides;
import com.company.crm.common.period.ReportingPeriods;
import com.company.crm.lead.dto.response.LeadSummaryDto;
import com.company.crm.lead.repository.LeadSummaryQuery;
import com.company.crm.campaign.entity.Campaign;
import com.company.crm.campaign.repository.CampaignRepository;
import com.company.crm.common.enums.CustomerStatus;
import com.company.crm.common.enums.IndustryType;
import com.company.crm.common.enums.LeadSource;
import com.company.crm.common.enums.LeadStage;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.lead.dto.request.LeadReqDto;
import com.company.crm.lead.dto.request.LeadStageReqDto;
import com.company.crm.lead.dto.response.LeadResDto;
import com.company.crm.lead.dto.response.LeadTeamOptionDto;
import com.company.crm.sales_team.repository.SalesTeamRepository;
import com.company.crm.lead.entity.Lead;
import com.company.crm.lead.mapper.LeadMapper;
import com.company.crm.lead.repository.LeadRepository;
import com.company.crm.sales.service.OpportunityService;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.company.crm.notification.event.RecordAssignedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final DataScopeResolver dataScopeResolver;
    private final LeadSummaryQuery leadSummaryQuery;
    private final ReportingPeriods reportingPeriods;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final CampaignRepository campaignRepository;
    private final OpportunityService opportunityService;
    private final LeadMapper leadMapper;
    private final SalesTeamRepository salesTeamRepository;
    private final LeadAssignmentRuleService assignmentRuleService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final PageRequestFactory pageRequestFactory;

    // open-in-view is disabled (see application.properties) — the mapper walks lazy
    // associations (owner, convertedCustomer, tenant), so the session must stay open through mapping.
    @Transactional(readOnly = true)
    public List<LeadResDto> listLeads(User currentUser, String stage, String source, String industry, String search,
                                      Long ownerId) {
        return listLeads(currentUser, stage, source, industry, search, ownerId, null, null);
    }

    /** FR-AI-1.3: also by AI score — at least {@code minScore}, or one label (High / Medium / Low). */
    @Transactional(readOnly = true)
    public List<LeadResDto> listLeads(User currentUser, String stage, String source, String industry, String search,
                                      Long ownerId, Integer minScore, String scoreLabel) {
        // NFR-7.1b: filtered in the database rather than in memory.
        return leadRepository.findAll(filters(currentUser, stage, source, industry, search, ownerId, minScore, scoreLabel), LIST_ORDER).stream()
                .map(leadMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public LeadResDto getLead(User currentUser, Long leadId) {
        Lead lead = findLead(currentUser, leadId);
        assertAccess(currentUser, lead);
        return leadMapper.toDto(lead);
    }

    @Transactional
    public LeadResDto createLead(User currentUser, LeadReqDto dto) {
        Lead lead = new Lead();
        lead.setTenant(currentUser.getTenant());
        lead.setCreatedBy(currentUser);
        lead.setStage(LeadStage.NEW_LEAD);
        applyFields(currentUser, lead, dto);
        // FR-2.3: auto-assignment rules decide the owner/team when none was picked. A sales
        // executive's own new lead stays theirs.
        if (dto.getOwnerId() == null && currentUser.getRole().getName() != RoleType.SALES_EXECUTIVE) {
            assignmentRuleService.apply(lead);
        }
        Lead saved = leadRepository.save(lead);
        announceAssignment(currentUser, null, saved);
        eventPublisher.publishEvent(new LeadChangedEvent(saved.getTenant().getId(), saved.getId())); // FR-AI-1.2
        return leadMapper.toDto(saved);
    }

    @Transactional
    public LeadResDto updateLead(User currentUser, Long leadId, LeadReqDto dto) {
        Lead lead = findLead(currentUser, leadId);
        assertAccess(currentUser, lead);
        Long previousOwnerId = lead.getOwner() != null ? lead.getOwner().getId() : null;
        applyFields(currentUser, lead, dto);
        Lead saved = leadRepository.save(lead);
        announceAssignment(currentUser, previousOwnerId, saved);
        eventPublisher.publishEvent(new LeadChangedEvent(saved.getTenant().getId(), saved.getId())); // FR-AI-1.2
        return leadMapper.toDto(saved);
    }

    @Transactional
    public void deleteLead(User currentUser, Long leadId) {
        Lead lead = findLead(currentUser, leadId);
        assertAccess(currentUser, lead);
        leadRepository.delete(lead);
    }

    /**
     * FR-2.4/BR-1: move a lead through the pipeline. A forward jump requires
     * {@code force=true} (logically the "explicit user override" BR-1 calls for);
     * moving to the same or an earlier stage is always allowed (e.g. reopening a lead).
     * FR-2.6/BR-2: reaching CONVERTED auto-creates a linked Customer, once.
     */
    @Transactional
    public LeadResDto changeStage(User currentUser, Long leadId, LeadStageReqDto dto) {
        Lead lead = findLead(currentUser, leadId);
        assertAccess(currentUser, lead);

        LeadStage target = parseStage(dto.getStage());
        LeadStage current = lead.getStage();

        boolean isForwardSkip = target.getOrder() > current.getOrder() + 1;
        if (isForwardSkip && !dto.isForce()) {
            // STAGE_SKIP: the client may ask the user to confirm the override and resend with force=true.
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Leads must move through the pipeline in order. Pass force=true to skip stages.", "STAGE_SKIP");
        }
        if (isForwardSkip) {
            auditService.record(currentUser, AuditAction.LEAD_STAGE_OVERRIDDEN, "lead", lead.getId(),
                    StageOverrides.details(current.getDbValue(), target.getDbValue(), target.getOrder() - current.getOrder() - 1,
                            dto.getOverrideReason()));
        }

        lead.setStage(target);

        // BR-2: a converted lead auto-generates a linked Customer record AND Sales Opportunity.
        if (target == LeadStage.CONVERTED && lead.getConvertedCustomer() == null) {
            Customer customer = convertToCustomer(lead);
            lead.setConvertedCustomer(customer);
            opportunityService.createFromLeadConversion(lead, customer);
        }

        Lead saved = leadRepository.save(lead);
        eventPublisher.publishEvent(new LeadChangedEvent(saved.getTenant().getId(), saved.getId())); // FR-AI-1.2
        return leadMapper.toDto(saved);
    }

    /** FR-2.3/LEADS_ASSIGN: reassigning ownership is restricted to admin/sales_manager at the controller. */
    @Transactional
    public LeadResDto assignLead(User currentUser, Long leadId, Long ownerId) {
        Lead lead = findLead(currentUser, leadId);
        Long previousOwnerId = lead.getOwner() != null ? lead.getOwner().getId() : null;
        lead.setOwner(resolveOwner(currentUser, ownerId));
        Lead saved = leadRepository.save(lead);
        announceAssignment(currentUser, previousOwnerId, saved);
        return leadMapper.toDto(saved);
    }

    /** FR-M.4: tell the new owner (unless they assigned it to themselves); sent once the change commits. */
    private void announceAssignment(User currentUser, Long previousOwnerId, Lead lead) {
        User owner = lead.getOwner();
        if (owner == null || owner.getId().equals(previousOwnerId) || owner.getId().equals(currentUser.getId())) {
            return;
        }
        eventPublisher.publishEvent(new RecordAssignedEvent(lead.getTenant().getId(), owner.getId(), currentUser.getId(),
                currentUser.getFullName(), "lead", lead.getId(), "New lead assigned: " + lead.getLeadName(),
                lead.getCompanyName() != null ? lead.getCompanyName() : "Lead"));
    }

    /**
     * FR-2.3: assign the lead to a sales team of this tenant, or clear it (teamId null). The owner is
     * kept; the team's manager now sees the lead even before a team member owns it.
     */
    @Transactional
    public LeadResDto assignTeam(User currentUser, Long leadId, Long teamId) {
        Lead lead = findLead(currentUser, leadId);
        assertAccess(currentUser, lead);
        lead.setTeam(teamId == null ? null : salesTeamRepository.findByIdAndTenantId(teamId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Team must belong to your tenant")));
        return leadMapper.toDto(leadRepository.save(lead));
    }

    /** FR-2.3: the tenant's sales teams, for the "assign to team" picker. */
    @Transactional(readOnly = true)
    public List<LeadTeamOptionDto> listAssignableTeams(User currentUser) {
        return salesTeamRepository.findByTenantIdOrderByNameAsc(requireTenantId(currentUser)).stream()
                .map(team -> new LeadTeamOptionDto(team.getId(), team.getName()))
                .toList();
    }

    // ==================== Helpers ====================

    private Customer convertToCustomer(Lead lead) {
        Customer customer = new Customer();
        customer.setTenant(lead.getTenant());
        customer.setCompanyName(lead.getCompanyName());
        customer.setIndustry(lead.getIndustry().getDbValue());
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setEmail(lead.getContactEmail());
        customer.setPhone(lead.getContactPhone());
        customer.setOwner(lead.getOwner());
        customer.setCreatedBy(lead.getCreatedBy());
        customer.setNotes("Converted from lead: " + lead.getLeadName());
        return customerRepository.save(customer);
    }

    private void applyFields(User currentUser, Lead lead, LeadReqDto dto) {
        lead.setLeadName(dto.getLeadName());
        lead.setCompanyName(dto.getCompanyName());
        lead.setContactEmail(dto.getContactEmail());
        lead.setContactPhone(dto.getContactPhone());
        lead.setIndustry(parseIndustry(dto.getIndustry()));
        lead.setSource(parseSource(dto.getSource()));
        // FR-2.5: a new follow-up date gets its own reminder.
        if (!Objects.equals(lead.getFollowUpDate(), dto.getFollowUpDate())) {
            lead.setFollowUpNotifiedAt(null);
        }
        lead.setFollowUpDate(dto.getFollowUpDate());
        lead.setNotes(dto.getNotes());
        lead.setBudget(dto.getBudget());
        lead.setCampaign(dto.getCampaignId() != null ? resolveCampaign(currentUser, dto.getCampaignId()) : null);

        // sales_executive may work a lead without reassigning it — only honor an
        // ownerId change here for roles that hold LEADS_ASSIGN (admin/sales_manager).
        // A brand-new lead still defaults to its creator either way.
        if (lead.getId() == null) {
            lead.setOwner(dto.getOwnerId() != null ? resolveOwner(currentUser, dto.getOwnerId()) : currentUser);
        } else if (dto.getOwnerId() != null && currentUser.getRole().getName() != RoleType.SALES_EXECUTIVE) {
            lead.setOwner(resolveOwner(currentUser, dto.getOwnerId()));
        }
    }

    private Campaign resolveCampaign(User currentUser, Long campaignId) {
        Campaign campaign = campaignRepository.findByIdAndTenantId(campaignId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Campaign must belong to your tenant"));
        return campaign;
    }

    private User resolveOwner(User currentUser, Long ownerId) {
        User owner = userRepository.findByIdAndTenantId(ownerId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Owner must belong to your tenant"));
        return owner;
    }

    private IndustryType parseIndustry(String rawIndustry) {
        try {
            return IndustryType.fromDbValue(rawIndustry);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown industry: " + rawIndustry);
        }
    }

    private LeadSource parseSource(String rawSource) {
        try {
            return LeadSource.fromDbValue(rawSource);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown lead source: " + rawSource);
        }
    }

    private LeadStage parseStage(String rawStage) {
        try {
            return LeadStage.fromDbValue(rawStage);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown lead stage: " + rawStage);
        }
    }

    private Lead findLead(User currentUser, Long leadId) {
        Lead lead = leadRepository.findByIdAndTenantId(leadId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Lead not found"));
        return lead;
    }

    /**
     * sales_executive may only access leads assigned to them ("own" data scope). A sales manager
     * may also access leads assigned to a team they manage (FR-2.3).
     */
    private void assertAccess(User currentUser, Lead lead) {
        if (lead.getTeam() != null && dataScopeResolver.managedTeamIds(currentUser).contains(lead.getTeam().getId())) {
            return;
        }
        dataScopeResolver.assertCanAccess(currentUser,
                lead.getOwner() != null ? lead.getOwner().getId() : null, "lead");
    }

    /** NFR-7.1b: one page of the same list (opt-in via page/size); newest first unless {@code sort} says otherwise. */
    @Transactional(readOnly = true)
    public PageResponse<LeadResDto> listLeadsPage(User currentUser, String stage, String source, String industry, String search,
                                                  Long ownerId, Integer minScore, String scoreLabel, Integer page, Integer size, String sort) {
        Pageable pageable = pageRequestFactory.of(page, size, sort, SORTABLE_FIELDS, PAGE_ORDER);
        return PageResponse.of(leadRepository.findAll(filters(currentUser, stage, source, industry, search, ownerId, minScore, scoreLabel),
                pageable), leadMapper::toDto);
    }

    private static final Sort LIST_ORDER = Sort.by(Sort.Direction.ASC, "id");
    private static final Sort PAGE_ORDER = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
    private static final Map<String, String> SORTABLE_FIELDS = Map.of("createdAt", "createdAt", "leadName", "leadName",
            "companyName", "companyName", "followUpDate", "followUpDate", "stage", "stage", "aiScore", "aiScore");

    /** Tenant, data scope (FR-2.3 incl. managed teams), then the optional filters — FR-2.7 owner within the scope. */
    private Specification<Lead> filters(User currentUser, String stage, String source, String industry, String search, Long ownerId,
                                        Integer minScore, String scoreLabel) {
        DataScope scope = dataScopeResolver.resolve(currentUser);
        Specification<Lead> spec = ScopeSpecifications.inTenant(requireTenantId(currentUser));
        return spec.and(LeadSpecifications.visible(scope, scope.isTenantWide() ? List.of() : dataScopeResolver.managedTeamIds(currentUser)))
                .and(ScopeSpecifications.hasEnum("stage", stage, LeadStage.class, LeadStage::getDbValue))
                .and(ScopeSpecifications.hasEnum("source", source, LeadSource.class, LeadSource::getDbValue))
                .and(ScopeSpecifications.hasEnum("industry", industry, IndustryType.class, IndustryType::getDbValue))
                .and(LeadSpecifications.matches(search))
                .and(ScopeSpecifications.ownedBy(ownerId))
                .and(LeadSpecifications.scoreAtLeast(minScore))
                .and(ScopeSpecifications.hasValue("aiScoreLabel", scoreLabel));
    }

    /** Dashboard counts, aggregated in the database within the caller's data scope. */
    @Transactional(readOnly = true)
    public LeadSummaryDto getSummary(User currentUser) {
        return leadSummaryQuery.summarize(requireTenantId(currentUser), dataScopeResolver.resolve(currentUser),
                reportingPeriods.startOfMonth());
    }

    /** Records the user may see, as decided by DataScopeResolver (tenant-wide, team or own). */
    private List<Lead> findInScope(User currentUser) {
        Long tenantId = requireTenantId(currentUser);
        DataScope scope = dataScopeResolver.resolve(currentUser);
        if (scope.isTenantWide()) {
            return leadRepository.findByTenantId(tenantId);
        }
        // FR-2.3: a sales manager also sees leads assigned to the teams they manage.
        List<Long> teamIds = dataScopeResolver.managedTeamIds(currentUser);
        return teamIds.isEmpty()
                ? leadRepository.findByTenantIdAndOwnerIdIn(tenantId, scope.ownerIds())
                : leadRepository.findInScopeOrTeams(tenantId, scope.ownerIds(), teamIds);
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Leads are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
