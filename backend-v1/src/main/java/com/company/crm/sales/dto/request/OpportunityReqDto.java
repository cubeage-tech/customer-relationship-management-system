package com.company.crm.sales.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class OpportunityReqDto {

    @NotNull
    private Long customerId;

    private String productService;

    @NotNull
    @DecimalMin(value = "0", message = "Deal value cannot be negative")
    private BigDecimal dealValue;

    private LocalDate expectedClosingDate;

    /** Salesperson this deal is assigned to. Defaults to the creator when omitted. */
    private Long ownerId;
}
