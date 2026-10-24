package com.company.crm.sales.service;

import com.company.crm.common.audit.AuditService;
import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.period.ReportingPeriods;
import com.company.crm.sales.dto.response.OpportunityKpiDto;
import com.company.crm.sales.repository.OpportunitySummaryQuery;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.lead.entity.Lead;
import com.company.crm.sales.dto.request.OpportunityReqDto;
import com.company.crm.sales.dto.request.OpportunityStageReqDto;
import com.company.crm.sales.dto.response.OpportunityResDto;
import com.company.crm.sales.dto.response.OpportunityStageSummaryDto;
import com.company.crm.sales.dto.response.SalesPerformanceDto;
import com.company.crm.sales_team.repository.SalesTeamRepository;
import com.company.crm.salesforce.service.CommissionService;
import com.company.crm.sales.entity.Opportunity;
import com.company.crm.sales.mapper.OpportunityMapper;
import com.company.crm.sales.repository.OpportunityRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.company.crm.notification.event.RecordAssignedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpportunityService {

    private final OpportunityRepository opportunityRepository;
    private final AuditService auditService;
    private final DataScopeResolver dataScopeResolver;
    private final OpportunitySummaryQuery opportunitySummaryQuery;
    private final ReportingPeriods reportingPeriods;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final OpportunityMapper opportunityMapper;
    private final SalesTeamRepository salesTeamRepository;
    private final PipelineStageService pipelineStageService;
    private final CommissionService commissionService;
    private final ApplicationEventPublisher eventPublisher;

    // open-in-view is disabled (see application.properties) — the mapper walks lazy
    // associations (customer, owner, lead, tenant), so the session must stay open through mapping.
    @Transactional(readOnly = true)
    public List<OpportunityResDto> listOpportunities(
            User currentUser, String stage, Long customerId, String search) {
        return listOpportunities(currentUser, stage, customerId, search, null);
    }

    /** ownerId narrows the caller's scope to one owner (FR-M.2: "assigned to me" in the mobile app). */
    @Transactional(readOnly = true)
    public List<OpportunityResDto> listOpportunities(
            User currentUser, String stage, Long customerId, String search, Long ownerId) {
        List<Opportunity> opportunities = findInScope(currentUser);

        return opportunities.stream()
                .filter(o -> ownerId == null || (o.getOwner() != null && ownerId.equals(o.getOwner().getId())))
                .filter(o -> stage == null || stage.isBlank() || o.getStage().equals(stage))
                .filter(o -> customerId == null || customerId.equals(o.getCustomer().getId()))
                .filter(o -> search == null || search.isBlank()
                        || o.getCustomer().getCompanyName().toLowerCase().contains(search.trim().toLowerCase())
                        || (o.getProductService() != null
                                && o.getProductService().toLowerCase().contains(search.trim().toLowerCase())))
                .map(opportunityMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public OpportunityResDto getOpportunity(User currentUser, Long opportunityId) {
        Opportunity opportunity = findOpportunity(currentUser, opportunityId);
        assertAccess(currentUser, opportunity);
        return opportunityMapper.toDto(opportunity);
    }

    /** FR-3.3: cumulative deal value per stage, for the pipeline/Kanban header. */
    @Transactional(readOnly = true)
    public List<OpportunityStageSummaryDto> getStageSummary(User currentUser) {
        // Aggregated in the database (GROUP BY stage). FR-3.2: the tenant's stages in pipeline order,
        // zero-filled — active ones, plus any deactivated stage that still holds deals.
        Long tenantId = requireTenantId(currentUser);
        Map<String, OpportunitySummaryQuery.StageTotals> totals =
                opportunitySummaryQuery.totalsByStage(tenantId, dataScopeResolver.resolve(currentUser));

        return pipelineStageService.stages(tenantId).stream()
                .filter(stage -> stage.isActive() || totals.containsKey(stage.getCode()))
                .map(stage -> {
                    OpportunitySummaryQuery.StageTotals stageTotals = totals.get(stage.getCode());
                    return new OpportunityStageSummaryDto(
                            stage.getCode(),
                            stageTotals == null ? 0 : stageTotals.count(),
                            stageTotals == null ? BigDecimal.ZERO : stageTotals.totalValue(),
                            stage.getLabel());
                })
                .toList();
    }

    /** Dashboard pipeline KPIs (open value, closing this week, won this month/quarter), in one query. */
    @Transactional(readOnly = true)
    public OpportunityKpiDto getKpis(User currentUser) {
        return opportunitySummaryQuery.kpis(requireTenantId(currentUser), dataScopeResolver.resolve(currentUser),
                reportingPeriods.startOfWeek(), reportingPeriods.endOfWeek(),
                reportingPeriods.startOfMonth(), reportingPeriods.startOfQuarter());
    }

    /**
     * FR-3.5: open/won/lost count and value per salesperson (owner) and per sales team (the owner's
     * team), over the opportunities the caller may see. Sorted by won value, then open value.
     */
    @Transactional(readOnly = true)
    public SalesPerformanceDto getPerformance(User currentUser) {
        Long tenantId = requireTenantId(currentUser);
        Map<Long, String> teamNames = new HashMap<>();
        salesTeamRepository.findByTenantIdOrderByNameAsc(tenantId).forEach(t -> teamNames.put(t.getId(), t.getName()));

        Map<Long, PerformanceTotals> byOwner = new LinkedHashMap<>();
        Map<Long, PerformanceTotals> byTeam = new LinkedHashMap<>();
        for (Opportunity o : findInScope(currentUser)) {
            User owner = o.getOwner();
            Long ownerId = owner != null ? owner.getId() : null;
            Long teamId = owner != null && teamNames.containsKey(owner.getTeamId()) ? owner.getTeamId() : null;
            byOwner.computeIfAbsent(ownerId, id -> new PerformanceTotals(id, owner != null ? owner.getFullName() : "Unassigned")).add(o);
            byTeam.computeIfAbsent(teamId, id -> new PerformanceTotals(id, id != null ? teamNames.get(id) : "No team")).add(o);
        }
        return new SalesPerformanceDto(toRows(byOwner), toRows(byTeam));
    }

    private static List<SalesPerformanceDto.Row> toRows(Map<Long, PerformanceTotals> totals) {
        return totals.values().stream()
                .sorted(Comparator.comparing((PerformanceTotals t) -> t.wonValue).reversed()
                        .thenComparing(t -> t.openValue, Comparator.reverseOrder())
                        .thenComparing(t -> t.name))
                .map(PerformanceTotals::toRow)
                .toList();
    }

    /** Running totals for one performance row. */
    private static final class PerformanceTotals {
        private final Long id;
        private final String name;
        private long openCount, wonCount, lostCount;
        private BigDecimal openValue = BigDecimal.ZERO, wonValue = BigDecimal.ZERO, lostValue = BigDecimal.ZERO;

        private PerformanceTotals(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        private void add(Opportunity o) {
            BigDecimal value = o.getDealValue() != null ? o.getDealValue() : BigDecimal.ZERO;
            switch (o.getStage()) {
                case PipelineStageService.WON -> { wonCount++; wonValue = wonValue.add(value); }
                case PipelineStageService.LOST -> { lostCount++; lostValue = lostValue.add(value); }
                default -> { openCount++; openValue = openValue.add(value); }
            }
        }

        private SalesPerformanceDto.Row toRow() {
            return new SalesPerformanceDto.Row(id, name, openCount, openValue, wonCount, wonValue, lostCount, lostValue);
        }
    }

    @Transactional
    public OpportunityResDto createOpportunity(User currentUser, OpportunityReqDto dto) {
        Opportunity opportunity = new Opportunity();
        opportunity.setTenant(currentUser.getTenant());
        opportunity.setCreatedBy(currentUser);
        opportunity.setCustomer(resolveCustomer(currentUser, dto.getCustomerId()));
        opportunity.setStage(pipelineStageService.firstOpenStage(requireTenantId(currentUser)));
        applyFields(currentUser, opportunity, dto);
        Opportunity saved = opportunityRepository.save(opportunity);
        auditService.record(currentUser, AuditAction.OPPORTUNITY_CREATED, "opportunity", saved.getId(), summary(saved));
        announceAssignment(currentUser, null, saved);
        return opportunityMapper.toDto(saved);
    }

    @Transactional
    public OpportunityResDto updateOpportunity(User currentUser, Long opportunityId, OpportunityReqDto dto) {
        Opportunity opportunity = findOpportunity(currentUser, opportunityId);
        assertAccess(currentUser, opportunity);
        opportunity.setCustomer(resolveCustomer(currentUser, dto.getCustomerId()));
        Long previousOwnerId = opportunity.getOwner() != null ? opportunity.getOwner().getId() : null;
        applyFields(currentUser, opportunity, dto);
        Opportunity saved = opportunityRepository.save(opportunity);
        commissionService.onWonDealEdited(saved);
        auditService.record(currentUser, AuditAction.OPPORTUNITY_UPDATED, "opportunity", saved.getId(), summary(saved));
        announceAssignment(currentUser, previousOwnerId, saved);
        return opportunityMapper.toDto(saved);
    }

    @Transactional
    public void deleteOpportunity(User currentUser, Long opportunityId) {
        Opportunity opportunity = findOpportunity(currentUser, opportunityId);
        assertAccess(currentUser, opportunity);
        opportunityRepository.delete(opportunity);
    }

    /** FR-3.2/FR-3.4: change stage; a "lost" stage requires a loss reason (acceptance criterion). */
    @Transactional
    public OpportunityResDto changeStage(User currentUser, Long opportunityId, OpportunityStageReqDto dto) {
        Opportunity opportunity = findOpportunity(currentUser, opportunityId);
        assertAccess(currentUser, opportunity);

        String target = dto.getStage();
        // FR-3.2: an active stage of the tenant's pipeline (re-saving the current one is always fine).
        if (!target.equals(opportunity.getStage())) {
            pipelineStageService.requireActive(requireTenantId(currentUser), target);
        }
        if (PipelineStageService.LOST.equals(target) && (dto.getLossReason() == null || dto.getLossReason().isBlank())) {
            throw ApiException.badRequest("A loss reason is required to mark this opportunity as lost");
        }

        String previousStage = opportunity.getStage();
        opportunity.setStage(target);
        opportunity.setLossReason(PipelineStageService.LOST.equals(target) ? dto.getLossReason() : null);
        opportunity.setStageChangedAt(LocalDateTime.now());
        opportunity.setStageChangedBy(currentUser);

        Opportunity saved = opportunityRepository.save(opportunity);
        // AC-8.2: winning a deal records the owner's commission (re-opening it voids it).
        commissionService.onStageChanged(saved, previousStage);
        auditService.record(currentUser, AuditAction.OPPORTUNITY_STAGE_CHANGED, "opportunity", saved.getId(),
                "stage " + previousStage + " → " + saved.getStage() + ", dealValue=" + saved.getDealValue());
        return opportunityMapper.toDto(saved);
    }

    /**
     * BR-2: called by LeadService when a lead is converted — creates a draft opportunity
     * linked to the newly-created customer, so the pipeline has an entry point immediately.
     */
    @Transactional
    public Opportunity createFromLeadConversion(Lead lead, Customer customer) {
        Opportunity opportunity = new Opportunity();
        opportunity.setTenant(lead.getTenant());
        opportunity.setCustomer(customer);
        opportunity.setLead(lead);
        opportunity.setOwner(lead.getOwner());
        opportunity.setCreatedBy(lead.getCreatedBy());
        opportunity.setStage(pipelineStageService.firstOpenStage(lead.getTenant().getId()));
        opportunity.setDealValue(BigDecimal.ZERO);
        return opportunityRepository.save(opportunity);
    }

    // ==================== Helpers ====================

    private void applyFields(User currentUser, Opportunity opportunity, OpportunityReqDto dto) {
        opportunity.setProductService(dto.getProductService());
        opportunity.setDealValue(dto.getDealValue());
        opportunity.setExpectedClosingDate(dto.getExpectedClosingDate());

        if (opportunity.getId() == null) {
            opportunity.setOwner(dto.getOwnerId() != null ? resolveOwner(currentUser, dto.getOwnerId()) : currentUser);
        } else if (dto.getOwnerId() != null) {
            opportunity.setOwner(resolveOwner(currentUser, dto.getOwnerId()));
        }
    }

    /** NFR-7.2d audit detail. */
    private static String summary(Opportunity o) {
        return "customer=" + o.getCustomer().getCompanyName() + ", dealValue=" + o.getDealValue() + ", stage=" + o.getStage()
                + ", owner=" + (o.getOwner() != null ? o.getOwner().getFullName() : "none");
    }

    /** FR-M.4: tell the new owner (unless they assigned it to themselves); sent once the change commits. */
    private void announceAssignment(User currentUser, Long previousOwnerId, Opportunity opportunity) {
        User owner = opportunity.getOwner();
        if (owner == null || owner.getId().equals(previousOwnerId) || owner.getId().equals(currentUser.getId())) {
            return;
        }
        String what = opportunity.getProductService() != null ? opportunity.getProductService() : "Opportunity";
        eventPublisher.publishEvent(new RecordAssignedEvent(opportunity.getTenant().getId(), owner.getId(), currentUser.getId(),
                currentUser.getFullName(), "opportunity", opportunity.getId(),
                "Opportunity assigned: " + opportunity.getCustomer().getCompanyName(), what));
    }

    private Customer resolveCustomer(User currentUser, Long customerId) {
        Customer customer = customerRepository.findByIdAndTenantId(customerId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Customer must belong to your tenant"));
        return customer;
    }

    private User resolveOwner(User currentUser, Long ownerId) {
        User owner = userRepository.findByIdAndTenantId(ownerId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Owner must belong to your tenant"));
        return owner;
    }

    private Opportunity findOpportunity(User currentUser, Long opportunityId) {
        Opportunity opportunity = opportunityRepository.findByIdAndTenantId(opportunityId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Opportunity not found"));
        return opportunity;
    }

    /** sales_executive may only access opportunities assigned to them ("own" data scope). */
    private void assertAccess(User currentUser, Opportunity opportunity) {
        dataScopeResolver.assertCanAccess(currentUser,
                opportunity.getOwner() != null ? opportunity.getOwner().getId() : null, "opportunity");
    }

    /** Records the user may see, as decided by DataScopeResolver (tenant-wide, team or own). */
    private List<Opportunity> findInScope(User currentUser) {
        Long tenantId = requireTenantId(currentUser);
        DataScope scope = dataScopeResolver.resolve(currentUser);
        return scope.isTenantWide()
                ? opportunityRepository.findByTenantId(tenantId)
                : opportunityRepository.findByTenantIdAndOwnerIdIn(tenantId, scope.ownerIds());
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Opportunities are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
