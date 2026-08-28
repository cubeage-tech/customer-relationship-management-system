package com.company.crm.quotation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ProductResDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal unitPrice;
    private boolean active;
}
