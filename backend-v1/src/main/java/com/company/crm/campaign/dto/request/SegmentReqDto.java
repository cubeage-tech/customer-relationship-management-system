package com.company.crm.campaign.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** FR-7.2: a segment's name and criteria; every criterion is optional, set ones must all match. */
@Getter
@Setter
public class SegmentReqDto {

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 500)
    private String description;

    /** Customer industry codes (any of). */
    private List<String> industries;

    /** Lead source the customer came from (website, referral, …). */
    private String leadSource;

    @Min(1) @Max(3650)
    private Integer purchasedWithinDays;

    @Min(1) @Max(3650)
    private Integer notPurchasedWithinDays;

    @DecimalMin("0")
    private BigDecimal minPurchaseValue;

    @Min(1) @Max(3650)
    private Integer contactedWithinDays;

    @Min(1) @Max(3650)
    private Integer notContactedWithinDays;
}
