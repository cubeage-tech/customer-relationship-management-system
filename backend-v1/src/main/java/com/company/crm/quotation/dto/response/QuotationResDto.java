package com.company.crm.quotation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class QuotationResDto {
    private Long id;
    private String quotationNumber;
    private Long customerId;
    private String customerName;
    private Long opportunityId;
    private String status;
    private String discountApprovalStatus;
    private String discountReviewNote;
    private LocalDate validUntil;
    private String notes;
    private Long ownerId;
    private String ownerName;
    private List<QuotationLineItemResDto> lineItems;
    private BigDecimal subtotal;
    private BigDecimal grandTotal;
    private LocalDateTime createdAt;
}
