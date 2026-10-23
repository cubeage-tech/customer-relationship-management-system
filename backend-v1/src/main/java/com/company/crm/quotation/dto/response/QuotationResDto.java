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
    /** FR-4.5: the customer's tracked view link (null until sent). */
    private String viewUrl;
    /** FR-4.5: when the customer first opened it. */
    private LocalDateTime viewedAt;
    /** FR-4.8: total line cost; null while any line has no cost. Internal only. */
    private BigDecimal estimatedCost;
    /** FR-4.8: grand total minus estimated cost; null when the cost is unknown. */
    private BigDecimal estimatedMargin;
    /** FR-4.8: margin as % of the grand total (1 decimal); null when unknown or the total is 0. */
    private BigDecimal estimatedMarginPercent;
    /** FR-4.8: how many line items still need a cost. */
    private long linesWithoutCost;
}
