package com.company.crm.campaign.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** FR-7.3: record that a customer used a promotion. */
@Getter
@Setter
public class RedemptionReqDto {

    @NotNull
    private Long customerId;

    /** Optional: the quotation it was applied to (must be the same customer's). */
    private Long quotationId;

    /** Optional: what the offer saved the customer. */
    @DecimalMin("0")
    private BigDecimal discountAmount;

    @Size(max = 500)
    private String notes;
}
