package com.company.crm.campaign.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** FR-7.2: a saved segment and how many customers it matches right now. */
public record SegmentResDto(Long id, String name, String description, List<String> industries, String leadSource,
                            Integer purchasedWithinDays, Integer notPurchasedWithinDays, BigDecimal minPurchaseValue,
                            Integer contactedWithinDays, Integer notContactedWithinDays, long customerCount) {

    /** FR-7.2: a matching customer, for the preview. */
    public record Member(Long id, String companyName, String industry, String email, String phone, boolean optedOut) {}
}
