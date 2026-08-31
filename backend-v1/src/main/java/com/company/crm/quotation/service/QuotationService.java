package com.company.crm.quotation.service;

import com.company.crm.common.enums.DiscountApprovalStatus;
import com.company.crm.common.enums.QuotationStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.quotation.dto.request.QuotationCustomerStatusReqDto;
import com.company.crm.quotation.dto.request.QuotationLineItemReqDto;
import com.company.crm.quotation.dto.request.QuotationReqDto;
import com.company.crm.quotation.dto.response.QuotationResDto;
import com.company.crm.quotation.entity.Quotation;
import com.company.crm.quotation.entity.QuotationLineItem;
import com.company.crm.quotation.mapper.QuotationMapper;
import com.company.crm.quotation.pdf.QuotationPdf;
import com.company.crm.quotation.repository.QuotationRepository;
import com.company.crm.sales.entity.Opportunity;
import com.company.crm.sales.repository.OpportunityRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuotationService {

    private final QuotationRepository quotationRepository;
    private final CustomerRepository customerRepository;
    private final OpportunityRepository opportunityRepository;
    private final UserRepository userRepository;
    private final QuotationMapper quotationMapper;
    private final QuotationPdf quotationPdf;

    @Value("${app.quotation.discount-approval-threshold-percent}")
    private BigDecimal discountApprovalThreshold;

    // open-in-view is disabled (see application.properties) — the mapper walks lazy
    // associations (customer, opportunity, owner, lineItems, tenant), so the session
    // must stay open through mapping.
    @Transactional(readOnly = true)
    public List<QuotationResDto> listQuotations(User currentUser, String status, Long customerId, String search) {
        List<Quotation> quotations = currentUser.getRole().getName() == RoleType.SALES_EXECUTIVE
                ? quotationRepository.findByTenantIdAndOwnerId(requireTenantId(currentUser), currentUser.getId())
                : quotationRepository.findByTenantId(requireTenantId(currentUser));

        return quotations.stream()
                .filter(q -> status == null || status.isBlank() || q.getStatus().getDbValue().equals(status))
                .filter(q -> customerId == null || customerId.equals(q.getCustomer().getId()))
                .filter(q -> search == null || search.isBlank()
                        || q.getQuotationNumber().toLowerCase().contains(search.trim().toLowerCase())
                        || q.getCustomer().getCompanyName().toLowerCase().contains(search.trim().toLowerCase()))
                .map(quotationMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuotationResDto getQuotation(User currentUser, Long quotationId) {
        Quotation quotation = findQuotation(currentUser, quotationId);
        assertAccess(currentUser, quotation);
        return quotationMapper.toDto(quotation);
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(User currentUser, Long quotationId) {
        Quotation quotation = findQuotation(currentUser, quotationId);
        assertAccess(currentUser, quotation);
        return quotationPdf.generate(quotation);
    }

    @Transactional
    public QuotationResDto createQuotation(User currentUser, QuotationReqDto dto) {
        Quotation quotation = new Quotation();
        quotation.setTenant(currentUser.getTenant());
        quotation.setCreatedBy(currentUser);
        quotation.setQuotationNumber(nextQuotationNumber(currentUser));
        quotation.setCustomer(resolveCustomer(currentUser, dto.getCustomerId()));
        applyFields(currentUser, quotation, dto);
        return quotationMapper.toDto(quotationRepository.save(quotation));
    }

    @Transactional
    public QuotationResDto updateQuotation(User currentUser, Long quotationId, QuotationReqDto dto) {
        Quotation quotation = findQuotation(currentUser, quotationId);
        assertAccess(currentUser, quotation);
        quotation.setCustomer(resolveCustomer(currentUser, dto.getCustomerId()));
        applyFields(currentUser, quotation, dto);
        return quotationMapper.toDto(quotationRepository.save(quotation));
    }

    /** FR-4.3/BR-3 acceptance criterion: sending is blocked until a pending discount is approved. */
    @Transactional
    public QuotationResDto send(User currentUser, Long quotationId) {
        Quotation quotation = findQuotation(currentUser, quotationId);
        assertAccess(currentUser, quotation);

        if (quotation.getDiscountApprovalStatus() == DiscountApprovalStatus.PENDING
                || quotation.getDiscountApprovalStatus() == DiscountApprovalStatus.REJECTED) {
            throw ApiException.badRequest("This quotation's discount needs approval before it can be sent");
        }

        quotation.setStatus(QuotationStatus.PENDING);
        return quotationMapper.toDto(quotationRepository.save(quotation));
    }

    /** FR-4.5: recorded on the customer's behalf by internal staff — there is no self-service portal yet. */
    @Transactional
    public QuotationResDto recordCustomerStatus(User currentUser, Long quotationId, QuotationCustomerStatusReqDto dto) {
        Quotation quotation = findQuotation(currentUser, quotationId);
        assertAccess(currentUser, quotation);

        QuotationStatus target = parseStatus(dto.getStatus());
        if (target == QuotationStatus.DRAFT) {
            throw ApiException.badRequest("A quotation cannot be reset to draft this way");
        }
        quotation.setStatus(target);
        return quotationMapper.toDto(quotationRepository.save(quotation));
    }

    @Transactional
    public QuotationResDto approveDiscount(User currentUser, Long quotationId) {
        Quotation quotation = findQuotation(currentUser, quotationId);
        if (quotation.getDiscountApprovalStatus() != DiscountApprovalStatus.PENDING) {
            throw ApiException.badRequest("This quotation has no discount awaiting approval");
        }
        quotation.setDiscountApprovalStatus(DiscountApprovalStatus.APPROVED);
        quotation.setDiscountReviewNote(null);
        return quotationMapper.toDto(quotationRepository.save(quotation));
    }

    @Transactional
    public QuotationResDto rejectDiscount(User currentUser, Long quotationId, String reason) {
        Quotation quotation = findQuotation(currentUser, quotationId);
        if (quotation.getDiscountApprovalStatus() != DiscountApprovalStatus.PENDING) {
            throw ApiException.badRequest("This quotation has no discount awaiting approval");
        }
        quotation.setDiscountApprovalStatus(DiscountApprovalStatus.REJECTED);
        quotation.setDiscountReviewNote(reason);
        return quotationMapper.toDto(quotationRepository.save(quotation));
    }

    // ==================== Helpers ====================

    private void applyFields(User currentUser, Quotation quotation, QuotationReqDto dto) {
        quotation.setOpportunity(resolveOpportunity(currentUser, dto.getOpportunityId()));
        quotation.setValidUntil(dto.getValidUntil());
        quotation.setNotes(dto.getNotes());

        if (quotation.getId() == null) {
            quotation.setOwner(dto.getOwnerId() != null ? resolveOwner(currentUser, dto.getOwnerId()) : currentUser);
        } else if (dto.getOwnerId() != null) {
            quotation.setOwner(resolveOwner(currentUser, dto.getOwnerId()));
        }

        quotation.getLineItems().clear();
        for (QuotationLineItemReqDto itemDto : dto.getLineItems()) {
            QuotationLineItem item = new QuotationLineItem();
            item.setQuotation(quotation);
            item.setProductName(itemDto.getProductName());
            item.setQuantity(itemDto.getQuantity());
            item.setUnitPrice(itemDto.getUnitPrice());
            item.setDiscountPercent(itemDto.getDiscountPercent());
            quotation.getLineItems().add(item);
        }

        // BR-3: any create/edit that leaves a line item's discount above the threshold
        // needs a fresh approval, even if a prior version of this quotation was already approved.
        boolean needsApproval = quotation.getMaxDiscountPercent().compareTo(discountApprovalThreshold) > 0;
        quotation.setDiscountApprovalStatus(needsApproval ? DiscountApprovalStatus.PENDING : DiscountApprovalStatus.NOT_REQUIRED);
        quotation.setDiscountReviewNote(null);
    }

    private Customer resolveCustomer(User currentUser, Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.badRequest("Customer not found"));
        if (!customer.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.badRequest("Customer must belong to your tenant");
        }
        return customer;
    }

    private Opportunity resolveOpportunity(User currentUser, Long opportunityId) {
        if (opportunityId == null) {
            return null;
        }
        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> ApiException.badRequest("Opportunity not found"));
        if (!opportunity.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.badRequest("Opportunity must belong to your tenant");
        }
        return opportunity;
    }

    private User resolveOwner(User currentUser, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.badRequest("Owner not found"));
        if (owner.getTenant() == null || !owner.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.badRequest("Owner must belong to your tenant");
        }
        return owner;
    }

    private String nextQuotationNumber(User currentUser) {
        long sequence = quotationRepository.countByTenantId(requireTenantId(currentUser)) + 1;
        return "QTN-" + String.format("%06d", sequence);
    }

    private QuotationStatus parseStatus(String rawStatus) {
        try {
            return QuotationStatus.fromDbValue(rawStatus);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown quotation status: " + rawStatus);
        }
    }

    private Quotation findQuotation(User currentUser, Long quotationId) {
        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> ApiException.notFound("Quotation not found"));
        if (!quotation.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.notFound("Quotation not found");
        }
        return quotation;
    }

    /** sales_executive may only access quotations assigned to them ("own" data scope). */
    private void assertAccess(User currentUser, Quotation quotation) {
        if (currentUser.getRole().getName() == RoleType.SALES_EXECUTIVE
                && (quotation.getOwner() == null || !quotation.getOwner().getId().equals(currentUser.getId()))) {
            throw ApiException.forbidden("You do not have access to this quotation");
        }
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Quotations are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
