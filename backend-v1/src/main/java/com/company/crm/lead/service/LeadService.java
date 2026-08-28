package com.company.crm.lead.service;

import com.company.crm.common.enums.CustomerStatus;
import com.company.crm.common.enums.IndustryType;
import com.company.crm.common.enums.LeadSource;
import com.company.crm.common.enums.LeadStage;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.lead.dto.request.LeadReqDto;
import com.company.crm.lead.dto.request.LeadStageReqDto;
import com.company.crm.lead.dto.response.LeadResDto;
import com.company.crm.lead.entity.Lead;
import com.company.crm.lead.mapper.LeadMapper;
import com.company.crm.lead.repository.LeadRepository;
import com.company.crm.sales.service.OpportunityService;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final OpportunityService opportunityService;
    private final LeadMapper leadMapper;

    // open-in-view is disabled (see application.properties) — the mapper walks lazy
    // associations (owner, convertedCustomer, tenant), so the session must stay open through mapping.
    @Transactional(readOnly = true)
    public List<LeadResDto> listLeads(User currentUser, String stage, String source, String industry, String search) {
        List<Lead> leads = currentUser.getRole().getName() == RoleType.SALES_EXECUTIVE
                ? leadRepository.findByTenantIdAndOwnerId(requireTenantId(currentUser), currentUser.getId())
                : leadRepository.findByTenantId(requireTenantId(currentUser));

        return leads.stream()
                .filter(l -> stage == null || stage.isBlank() || l.getStage().getDbValue().equals(stage))
                .filter(l -> source == null || source.isBlank() || l.getSource().getDbValue().equals(source))
                .filter(l -> industry == null || industry.isBlank() || l.getIndustry().getDbValue().equals(industry))
                .filter(l -> search == null || search.isBlank()
                        || l.getLeadName().toLowerCase().contains(search.trim().toLowerCase())
                        || l.getCompanyName().toLowerCase().contains(search.trim().toLowerCase()))
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
        return leadMapper.toDto(leadRepository.save(lead));
    }

    @Transactional
    public LeadResDto updateLead(User currentUser, Long leadId, LeadReqDto dto) {
        Lead lead = findLead(currentUser, leadId);
        assertAccess(currentUser, lead);
        applyFields(currentUser, lead, dto);
        return leadMapper.toDto(leadRepository.save(lead));
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
            throw ApiException.badRequest(
                    "Leads must move through the pipeline in order. Pass force=true to skip stages.");
        }

        lead.setStage(target);

        // BR-2: a converted lead auto-generates a linked Customer record AND Sales Opportunity.
        if (target == LeadStage.CONVERTED && lead.getConvertedCustomer() == null) {
            Customer customer = convertToCustomer(lead);
            lead.setConvertedCustomer(customer);
            opportunityService.createFromLeadConversion(lead, customer);
        }

        return leadMapper.toDto(leadRepository.save(lead));
    }

    /** FR-2.3/LEADS_ASSIGN: reassigning ownership is restricted to admin/sales_manager at the controller. */
    @Transactional
    public LeadResDto assignLead(User currentUser, Long leadId, Long ownerId) {
        Lead lead = findLead(currentUser, leadId);
        lead.setOwner(resolveOwner(currentUser, ownerId));
        return leadMapper.toDto(leadRepository.save(lead));
    }

    // ==================== Helpers ====================

    private Customer convertToCustomer(Lead lead) {
        Customer customer = new Customer();
        customer.setTenant(lead.getTenant());
        customer.setCompanyName(lead.getCompanyName());
        customer.setIndustry(lead.getIndustry());
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
        lead.setFollowUpDate(dto.getFollowUpDate());
        lead.setNotes(dto.getNotes());

        // sales_executive may work a lead without reassigning it — only honor an
        // ownerId change here for roles that hold LEADS_ASSIGN (admin/sales_manager).
        // A brand-new lead still defaults to its creator either way.
        if (lead.getId() == null) {
            lead.setOwner(dto.getOwnerId() != null ? resolveOwner(currentUser, dto.getOwnerId()) : currentUser);
        } else if (dto.getOwnerId() != null && currentUser.getRole().getName() != RoleType.SALES_EXECUTIVE) {
            lead.setOwner(resolveOwner(currentUser, dto.getOwnerId()));
        }
    }

    private User resolveOwner(User currentUser, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.badRequest("Owner not found"));
        if (owner.getTenant() == null || !owner.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.badRequest("Owner must belong to your tenant");
        }
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
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> ApiException.notFound("Lead not found"));
        if (!lead.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.notFound("Lead not found");
        }
        return lead;
    }

    /** sales_executive may only access leads assigned to them ("own" data scope). */
    private void assertAccess(User currentUser, Lead lead) {
        if (currentUser.getRole().getName() == RoleType.SALES_EXECUTIVE
                && (lead.getOwner() == null || !lead.getOwner().getId().equals(currentUser.getId()))) {
            throw ApiException.forbidden("You do not have access to this lead");
        }
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Leads are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
