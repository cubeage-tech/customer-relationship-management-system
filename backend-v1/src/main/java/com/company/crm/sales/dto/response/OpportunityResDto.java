package com.company.crm.sales.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OpportunityResDto {
    private Long id;
    private Long customerId;
    private String customerName;
    private Long leadId;
    private String productService;
    private BigDecimal dealValue;
    private LocalDate expectedClosingDate;
    private String stage;
    private String lossReason;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime stageChangedAt;
    private LocalDateTime createdAt;
}
