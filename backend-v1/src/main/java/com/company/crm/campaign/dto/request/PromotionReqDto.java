package com.company.crm.campaign.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** FR-7.3: create or edit a promotion. */
@Getter
@Setter
public class PromotionReqDto {

    @NotBlank
    @Size(max = 40)
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "The code can only use letters, digits, - and _")
    private String code;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 500)
    private String description;

    /** percent | amount */
    @NotBlank
    private String discountType;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal discountValue;

    private LocalDate validFrom;

    private LocalDate validUntil;

    @Min(1)
    private Integer maxRedemptions;

    /** Defaults to true. */
    private Boolean active;

    /** Optional: the campaign promoting this offer. */
    private Long campaignId;
}
