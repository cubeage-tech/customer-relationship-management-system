package com.company.crm.plan.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdatePlanPriceReqDto {

    @NotNull
    @DecimalMin(value = "0.0", message = "Monthly price cannot be negative")
    private BigDecimal monthlyPrice;

    @NotNull
    @DecimalMin(value = "0.0", message = "Annual price cannot be negative")
    private BigDecimal annualPrice;

    /** ISO 4217 currency code, e.g. INR, USD. Leave blank to keep the plan's current currency. */
    private String currency;
}
