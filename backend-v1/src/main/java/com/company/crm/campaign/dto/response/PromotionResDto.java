package com.company.crm.campaign.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** FR-7.3: a promotion with its usage; {@code redeemable} = active, in date and under its cap today. */
public record PromotionResDto(Long id, String code, String name, String description, String discountType,
                              BigDecimal discountValue, LocalDate validFrom, LocalDate validUntil, Integer maxRedemptions,
                              boolean active, Long campaignId, String campaignName, long redemptions, boolean redeemable) {

    /** FR-7.3: one redemption — shown on the promotion and on the customer's record. */
    public record Redemption(Long id, Long promotionId, String promotionCode, String promotionName,
                             Long customerId, String customerName, Long quotationId, String quotationNumber,
                             BigDecimal discountAmount, String notes, LocalDateTime redeemedAt, String recordedByName) {}
}
