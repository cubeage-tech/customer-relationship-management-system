package com.company.crm.quotation.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductReqDto {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    @DecimalMin(value = "0", message = "Unit price cannot be negative")
    private BigDecimal unitPrice;
}
