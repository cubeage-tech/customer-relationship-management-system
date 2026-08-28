package com.company.crm.quotation.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class QuotationLineItemReqDto {

    @NotBlank
    private String productName;

    @NotNull
    @DecimalMin(value = "0", inclusive = false, message = "Quantity must be greater than 0")
    private BigDecimal quantity;

    @NotNull
    @DecimalMin(value = "0", message = "Unit price cannot be negative")
    private BigDecimal unitPrice;

    @NotNull
    @DecimalMin(value = "0", message = "Discount must be between 0 and 100")
    @DecimalMax(value = "100", message = "Discount must be between 0 and 100")
    private BigDecimal discountPercent;
}
