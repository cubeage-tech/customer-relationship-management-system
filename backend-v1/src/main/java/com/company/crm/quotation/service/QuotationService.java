package com.company.crm.quotation.service;

import com.company.crm.quotation.dto.response.QuotationSummaryDto;
import com.company.crm.quotation.repository.QuotationSummaryQuery;
import com.company.crm.common.enums.DiscountApprovalStatus;
import com.company.crm.common.enums.QuotationStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.quotation.dto.request.QuotationCustomerStatusReqDto;
import com.company.crm.quotation.dto.request.QuotationLineItemReqDto;
import com.company.crm.quotation.dto.request.QuotationReqDto;
import com.company.crm.quotation.dto.response.PublicQuotationDto;
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
import com.company.crm.quotation.event.QuotationSentEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuotationService {

    /** FR-4.5: sent and awaiting the customer's answer — these can be viewed or expire. */
    private static final Set<QuotationStatus> OPEN_FOR_RESPONSE = Set.of(QuotationStatus.PENDING, QuotationStatus.VIEWED);
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final QuotationRepository quotationRepository;
    private final DataScopeResolver dataScopeResolver;
    private final QuotationSummaryQuery quotationSummaryQuery;
    private final CustomerRepository customerRepository;
    private final OpportunityRepository opportunityRepository;
    private final UserRepository userRepository;
    private final QuotationMapper quotationMapper;
    private final QuotationPdf quotationPdf;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.quotation.discount-approval-threshold-percent}")
    private BigDecimal discountApprovalThreshold;

    // open-in-view is disabled (see application.properties) — the mapper walks lazy
    // associations (customer, opportunity, owner, lineItems, tenant), so the session
    // must stay open through mapping.
    @Transactional(readOnly = true)
    public List<QuotationResDto> listQuotations(User currentUser, String status, Long customerId, String search) {
        List<Quotation> quotations = findInScope(currentUser);

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

        // FR-4.5: an already-expired offer would expire again at the next job run.
        if (quotation.getValidUntil() != null && quotation.getValidUntil().isBefore(LocalDate.now())) {
            throw ApiException.badRequest("This quotation's valid-until date has passed. Update it before sending");
        }

        quotation.setStatus(QuotationStatus.PENDING);
        if (quotation.getViewToken() == null) {
            quotation.setViewToken(newViewToken());
        }
        QuotationResDto saved = quotationMapper.toDto(quotationRepository.save(quotation));
        // After this transaction commits: FR-4.5 emails the customer their view link, and the
        // FR-5.3 SMS alert goes out if enabled for the tenant.
        eventPublisher.publishEvent(new QuotationSentEvent(quotation.getTenant().getId(), quotation.getId()));
        return saved;
    }

    /**
     * FR-4.5: the customer opens their view link. The first open of a pending quotation marks it
     * VIEWED; an offer past its valid-until date is expired first. 404 for an unknown token.
     * {@code byCustomer=false} (a CRM user previewing the link) leaves the view unrecorded.
     */
    @Transactional
    public PublicQuotationDto viewAsCustomer(String token, boolean byCustomer) {
        Quotation quotation = openViewLink(token, byCustomer);
        return new PublicQuotationDto(quotation.getTenant().getCompanyName(), quotation.getQuotationNumber(),
                quotation.getCustomer().getCompanyName(), quotation.getStatus().getDbValue(), quotation.getValidUntil(),
                quotation.getLineItems().stream().map(quotationMapper::toPublicLineItem).toList(),
                quotation.getSubtotal(), quotation.getGrandTotal());
    }

    /** A quotation PDF and the name to download it under. */
    public record CustomerPdf(String fileName, byte[] content) {}

    /** FR-4.5: PDF through the customer's view link (also counts as viewing it). */
    @Transactional
    public CustomerPdf customerPdf(String token, boolean byCustomer) {
        Quotation quotation = openViewLink(token, byCustomer);
        return new CustomerPdf("quotation-" + quotation.getQuotationNumber() + ".pdf", quotationPdf.generate(quotation));
    }

    /** FR-4.5: scheduled — pending/viewed quotations past their valid-until date become EXPIRED. */
    @Transactional
    public int expireOverdue() {
        return quotationRepository.expireOverdue(QuotationStatus.EXPIRED, OPEN_FOR_RESPONSE, LocalDate.now(), LocalDateTime.now());
    }

    private Quotation openViewLink(String token, boolean byCustomer) {
        Quotation quotation = (token == null || token.isBlank() ? Optional.<Quotation>empty() : quotationRepository.findByViewToken(token))
                .orElseThrow(() -> ApiException.notFound("This quotation link is not valid"));
        if (OPEN_FOR_RESPONSE.contains(quotation.getStatus())
                && quotation.getValidUntil() != null && quotation.getValidUntil().isBefore(LocalDate.now())) {
            quotation.setStatus(QuotationStatus.EXPIRED);
        }
        if (byCustomer && quotation.getStatus() == QuotationStatus.PENDING) {
            quotation.setStatus(QuotationStatus.VIEWED);
        }
        if (byCustomer && quotation.getViewedAt() == null) {
            quotation.setViewedAt(LocalDateTime.now());
        }
        return quotationRepository.save(quotation);
    }

    private static String newViewToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** FR-4.5: recorded on the customer's behalf by internal staff (approve/reject from the customer's reply). */
    @Transactional
    public QuotationResDto recordCustomerStatus(User currentUser, Long quotationId, QuotationCustomerStatusReqDto dto) {
        Quotation quotation = findQuotation(currentUser, quotationId);
        assertAccess(currentUser, quotation);

        QuotationStatus target = parseStatus(dto.getStatus());
        if (target == QuotationStatus.DRAFT) {
            throw ApiException.badRequest("A quotation cannot be reset to draft this way");
        }
        // FR-1.3: keep the first approval time; clear it if the approval is withdrawn.
        if (target == QuotationStatus.APPROVED && quotation.getCustomerApprovedAt() == null) {
            quotation.setCustomerApprovedAt(LocalDateTime.now());
        } else if (target != QuotationStatus.APPROVED) {
            quotation.setCustomerApprovedAt(null);
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
            item.setUnitCost(itemDto.getUnitCost());
            quotation.getLineItems().add(item);
        }

        // BR-3: any create/edit that leaves a line item's discount above the threshold
        // needs a fresh approval, even if a prior version of this quotation was already approved.
        boolean needsApproval = quotation.getMaxDiscountPercent().compareTo(discountApprovalThreshold) > 0;
        quotation.setDiscountApprovalStatus(needsApproval ? DiscountApprovalStatus.PENDING : DiscountApprovalStatus.NOT_REQUIRED);
        quotation.setDiscountReviewNote(null);
    }

    private Customer resolveCustomer(User currentUser, Long customerId) {
        Customer customer = customerRepository.findByIdAndTenantId(customerId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Customer must belong to your tenant"));
        return customer;
    }

    private Opportunity resolveOpportunity(User currentUser, Long opportunityId) {
        if (opportunityId == null) {
            return null;
        }
        Opportunity opportunity = opportunityRepository.findByIdAndTenantId(opportunityId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Opportunity must belong to your tenant"));
        return opportunity;
    }

    private User resolveOwner(User currentUser, Long ownerId) {
        User owner = userRepository.findByIdAndTenantId(ownerId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Owner must belong to your tenant"));
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
        Quotation quotation = quotationRepository.findByIdAndTenantId(quotationId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Quotation not found"));
        return quotation;
    }

    /** sales_executive may only access quotations assigned to them ("own" data scope). */
    private void assertAccess(User currentUser, Quotation quotation) {
        dataScopeResolver.assertCanAccess(currentUser,
                quotation.getOwner() != null ? quotation.getOwner().getId() : null, "quotation");
    }

    /** Dashboard counts and discount figures, aggregated in the database within the caller's data scope. */
    @Transactional(readOnly = true)
    public QuotationSummaryDto getSummary(User currentUser) {
        return quotationSummaryQuery.summarize(requireTenantId(currentUser), dataScopeResolver.resolve(currentUser));
    }

    /** Records the user may see, as decided by DataScopeResolver (tenant-wide, team or own). */
    private List<Quotation> findInScope(User currentUser) {
        Long tenantId = requireTenantId(currentUser);
        DataScope scope = dataScopeResolver.resolve(currentUser);
        return scope.isTenantWide()
                ? quotationRepository.findByTenantId(tenantId)
                : quotationRepository.findByTenantIdAndOwnerIdIn(tenantId, scope.ownerIds());
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Quotations are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
