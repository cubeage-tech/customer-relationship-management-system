package com.company.crm.sales.service;

import com.company.crm.common.enums.OpportunityStage;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.lead.entity.Lead;
import com.company.crm.sales.dto.request.OpportunityReqDto;
import com.company.crm.sales.dto.request.OpportunityStageReqDto;
import com.company.crm.sales.dto.response.OpportunityResDto;
import com.company.crm.sales.dto.response.OpportunityStageSummaryDto;
import com.company.crm.sales.entity.Opportunity;
import com.company.crm.sales.mapper.OpportunityMapper;
import com.company.crm.sales.repository.OpportunityRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpportunityService {

    private final OpportunityRepository opportunityRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final OpportunityMapper opportunityMapper;

    // open-in-view is disabled (see application.properties) — the mapper walks lazy
    // associations (customer, owner, lead, tenant), so the session must stay open through mapping.
    @Transactional(readOnly = true)
    public List<OpportunityResDto> listOpportunities(
            User currentUser, String stage, Long customerId, String search) {
        List<Opportunity> opportunities = currentUser.getRole().getName() == RoleType.SALES_EXECUTIVE
                ? opportunityRepository.findByTenantIdAndOwnerId(requireTenantId(currentUser), currentUser.getId())
                : opportunityRepository.findByTenantId(requireTenantId(currentUser));

        return opportunities.stream()
                .filter(o -> stage == null || stage.isBlank() || o.getStage().getDbValue().equals(stage))
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
        List<Opportunity> opportunities = currentUser.getRole().getName() == RoleType.SALES_EXECUTIVE
                ? opportunityRepository.findByTenantIdAndOwnerId(requireTenantId(currentUser), currentUser.getId())
                : opportunityRepository.findByTenantId(requireTenantId(currentUser));

        Map<OpportunityStage, List<Opportunity>> byStage = new LinkedHashMap<>();
        Arrays.stream(OpportunityStage.values()).forEach(stage -> byStage.put(stage, new java.util.ArrayList<>()));
        opportunities.forEach(o -> byStage.get(o.getStage()).add(o));

        return byStage.entrySet().stream()
                .map(entry -> new OpportunityStageSummaryDto(
                        entry.getKey().getDbValue(),
                        entry.getValue().size(),
                        entry.getValue().stream().map(Opportunity::getDealValue).reduce(BigDecimal.ZERO, BigDecimal::add)
                ))
                .toList();
    }

    @Transactional
    public OpportunityResDto createOpportunity(User currentUser, OpportunityReqDto dto) {
        Opportunity opportunity = new Opportunity();
        opportunity.setTenant(currentUser.getTenant());
        opportunity.setCreatedBy(currentUser);
        opportunity.setCustomer(resolveCustomer(currentUser, dto.getCustomerId()));
        applyFields(currentUser, opportunity, dto);
        return opportunityMapper.toDto(opportunityRepository.save(opportunity));
    }

    @Transactional
    public OpportunityResDto updateOpportunity(User currentUser, Long opportunityId, OpportunityReqDto dto) {
        Opportunity opportunity = findOpportunity(currentUser, opportunityId);
        assertAccess(currentUser, opportunity);
        opportunity.setCustomer(resolveCustomer(currentUser, dto.getCustomerId()));
        applyFields(currentUser, opportunity, dto);
        return opportunityMapper.toDto(opportunityRepository.save(opportunity));
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

        OpportunityStage target = parseStage(dto.getStage());
        if (target == OpportunityStage.LOST && (dto.getLossReason() == null || dto.getLossReason().isBlank())) {
            throw ApiException.badRequest("A loss reason is required to mark this opportunity as lost");
        }

        opportunity.setStage(target);
        opportunity.setLossReason(target == OpportunityStage.LOST ? dto.getLossReason() : null);
        opportunity.setStageChangedAt(LocalDateTime.now());
        opportunity.setStageChangedBy(currentUser);

        return opportunityMapper.toDto(opportunityRepository.save(opportunity));
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
        opportunity.setStage(OpportunityStage.QUALIFICATION);
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

    private Customer resolveCustomer(User currentUser, Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.badRequest("Customer not found"));
        if (!customer.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.badRequest("Customer must belong to your tenant");
        }
        return customer;
    }

    private User resolveOwner(User currentUser, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.badRequest("Owner not found"));
        if (owner.getTenant() == null || !owner.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.badRequest("Owner must belong to your tenant");
        }
        return owner;
    }

    private OpportunityStage parseStage(String rawStage) {
        try {
            return OpportunityStage.fromDbValue(rawStage);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown opportunity stage: " + rawStage);
        }
    }

    private Opportunity findOpportunity(User currentUser, Long opportunityId) {
        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> ApiException.notFound("Opportunity not found"));
        if (!opportunity.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.notFound("Opportunity not found");
        }
        return opportunity;
    }

    /** sales_executive may only access opportunities assigned to them ("own" data scope). */
    private void assertAccess(User currentUser, Opportunity opportunity) {
        if (currentUser.getRole().getName() == RoleType.SALES_EXECUTIVE
                && (opportunity.getOwner() == null || !opportunity.getOwner().getId().equals(currentUser.getId()))) {
            throw ApiException.forbidden("You do not have access to this opportunity");
        }
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Opportunities are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
