package com.company.crm.campaign.service;

import com.company.crm.campaign.dto.request.PromotionReqDto;
import com.company.crm.campaign.dto.request.RedemptionReqDto;
import com.company.crm.campaign.dto.response.PromotionResDto;
import com.company.crm.campaign.entity.Promotion;
import com.company.crm.campaign.entity.PromotionRedemption;
import com.company.crm.campaign.repository.CampaignRepository;
import com.company.crm.campaign.repository.PromotionRedemptionRepository;
import com.company.crm.campaign.repository.PromotionRepository;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.quotation.entity.Quotation;
import com.company.crm.quotation.repository.QuotationRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * FR-7.3: promotions/offers and their redemptions. Marketing manages offers (code, percent or
 * fixed discount, validity, optional cap and campaign); whoever handles the sale records that a
 * customer used one — at most once per customer, only while it is active, in date and under its cap.
 */
@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository redemptionRepository;
    private final CampaignRepository campaignRepository;
    private final CustomerRepository customerRepository;
    private final QuotationRepository quotationRepository;
    private final DataScopeResolver dataScopeResolver;

    @Transactional(readOnly = true)
    public List<PromotionResDto> list(User currentUser) {
        return promotionRepository.findByTenantIdOrderByCreatedAtDesc(requireTenantId(currentUser)).stream().map(this::toDto).toList();
    }

    @Transactional
    public PromotionResDto create(User currentUser, PromotionReqDto dto) {
        Long tenantId = requireTenantId(currentUser);
        if (promotionRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, dto.getCode().trim())) {
            throw ApiException.conflict("A promotion with this code already exists");
        }
        Promotion p = new Promotion();
        p.setTenant(currentUser.getTenant());
        p.setCreatedBy(currentUser);
        applyFields(currentUser, p, dto);
        return toDto(promotionRepository.save(p));
    }

    @Transactional
    public PromotionResDto update(User currentUser, Long promotionId, PromotionReqDto dto) {
        Promotion p = find(currentUser, promotionId);
        if (promotionRepository.existsByTenantIdAndCodeIgnoreCaseAndIdNot(p.getTenant().getId(), dto.getCode().trim(), promotionId)) {
            throw ApiException.conflict("A promotion with this code already exists");
        }
        applyFields(currentUser, p, dto);
        return toDto(promotionRepository.save(p));
    }

    @Transactional(readOnly = true)
    public List<PromotionResDto.Redemption> redemptions(User currentUser, Long promotionId) {
        Promotion p = find(currentUser, promotionId);
        return redemptionRepository.findByPromotionIdOrderByRedeemedAtDesc(p.getId()).stream().map(this::toDto).toList();
    }

    /** FR-7.3: the customer record's view — which offers this customer has used. */
    @Transactional(readOnly = true)
    public List<PromotionResDto.Redemption> customerRedemptions(User currentUser, Long customerId) {
        Customer customer = findCustomer(currentUser, customerId);
        return redemptionRepository.findByTenantIdAndCustomerIdOrderByRedeemedAtDesc(customer.getTenant().getId(), customerId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public PromotionResDto.Redemption redeem(User currentUser, Long promotionId, RedemptionReqDto dto) {
        Promotion p = find(currentUser, promotionId);
        Customer customer = findCustomer(currentUser, dto.getCustomerId());
        String reason = notRedeemableReason(p, LocalDate.now());
        if (reason != null) {
            throw ApiException.badRequest(reason);
        }
        if (redemptionRepository.existsByPromotionIdAndCustomerId(p.getId(), customer.getId())) {
            throw ApiException.conflict(customer.getCompanyName() + " has already used this promotion");
        }
        PromotionRedemption r = new PromotionRedemption();
        r.setTenant(p.getTenant());
        r.setPromotion(p);
        r.setCustomer(customer);
        if (dto.getQuotationId() != null) {
            Quotation q = quotationRepository.findByIdAndTenantId(dto.getQuotationId(), p.getTenant().getId())
                    .filter(quotation -> quotation.getCustomer().getId().equals(customer.getId()))
                    .orElseThrow(() -> ApiException.badRequest("The quotation must be this customer's"));
            r.setQuotation(q);
        }
        r.setDiscountAmount(dto.getDiscountAmount());
        r.setNotes(dto.getNotes() != null && !dto.getNotes().isBlank() ? dto.getNotes().trim() : null);
        r.setRecordedBy(currentUser);
        return toDto(redemptionRepository.save(r));
    }

    // ==================== Helpers ====================

    /** Null when the promotion can be redeemed today; otherwise why not. */
    private String notRedeemableReason(Promotion p, LocalDate today) {
        if (!p.isActive()) {
            return "This promotion is not active";
        }
        if (p.getValidFrom() != null && today.isBefore(p.getValidFrom())) {
            return "This promotion starts on " + p.getValidFrom();
        }
        if (p.getValidUntil() != null && today.isAfter(p.getValidUntil())) {
            return "This promotion ended on " + p.getValidUntil();
        }
        if (p.getMaxRedemptions() != null && redemptionRepository.countByPromotionId(p.getId()) >= p.getMaxRedemptions()) {
            return "This promotion has reached its redemption limit";
        }
        return null;
    }

    private void applyFields(User currentUser, Promotion p, PromotionReqDto dto) {
        String type = dto.getDiscountType().trim().toLowerCase(Locale.ROOT);
        if (!Promotion.PERCENT.equals(type) && !Promotion.AMOUNT.equals(type)) {
            throw ApiException.badRequest("The discount type must be percent or amount");
        }
        if (Promotion.PERCENT.equals(type) && dto.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.badRequest("A percentage discount can't exceed 100");
        }
        if (dto.getValidFrom() != null && dto.getValidUntil() != null && dto.getValidUntil().isBefore(dto.getValidFrom())) {
            throw ApiException.badRequest("The end date must be on or after the start date");
        }
        p.setCode(dto.getCode().trim().toUpperCase(Locale.ROOT));
        p.setName(dto.getName().trim());
        p.setDescription(dto.getDescription() != null && !dto.getDescription().isBlank() ? dto.getDescription().trim() : null);
        p.setDiscountType(type);
        p.setDiscountValue(dto.getDiscountValue());
        p.setValidFrom(dto.getValidFrom());
        p.setValidUntil(dto.getValidUntil());
        p.setMaxRedemptions(dto.getMaxRedemptions());
        p.setActive(dto.getActive() == null || dto.getActive());
        p.setCampaign(dto.getCampaignId() == null ? null : campaignRepository.findByIdAndTenantId(dto.getCampaignId(), requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Campaign must belong to your tenant")));
    }

    private PromotionResDto toDto(Promotion p) {
        return new PromotionResDto(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getDiscountType(), p.getDiscountValue(),
                p.getValidFrom(), p.getValidUntil(), p.getMaxRedemptions(), p.isActive(),
                p.getCampaign() != null ? p.getCampaign().getId() : null, p.getCampaign() != null ? p.getCampaign().getName() : null,
                redemptionRepository.countByPromotionId(p.getId()), notRedeemableReason(p, LocalDate.now()) == null);
    }

    private PromotionResDto.Redemption toDto(PromotionRedemption r) {
        return new PromotionResDto.Redemption(r.getId(), r.getPromotion().getId(), r.getPromotion().getCode(), r.getPromotion().getName(),
                r.getCustomer().getId(), r.getCustomer().getCompanyName(),
                r.getQuotation() != null ? r.getQuotation().getId() : null,
                r.getQuotation() != null ? r.getQuotation().getQuotationNumber() : null,
                r.getDiscountAmount(), r.getNotes(), r.getRedeemedAt(),
                r.getRecordedBy() != null ? r.getRecordedBy().getFullName() : null);
    }

    private Promotion find(User currentUser, Long promotionId) {
        return promotionRepository.findByIdAndTenantId(promotionId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Promotion not found"));
    }

    private Customer findCustomer(User currentUser, Long customerId) {
        Customer customer = customerRepository.findByIdAndTenantId(customerId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
        dataScopeResolver.assertCanAccess(currentUser, customer.getOwner() != null ? customer.getOwner().getId() : null, "customer");
        return customer;
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Promotions are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
