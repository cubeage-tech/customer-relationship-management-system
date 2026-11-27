package com.company.crm.quotation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * FR-4.5: what the customer sees through their view link — no internal fields (owner, notes,
 * discount approval, costs or margin).
 */
public record PublicQuotationDto(String companyName, String quotationNumber, String customerName,
                                 String status, LocalDate validUntil,
                                 List<LineItem> lineItems,
                                 BigDecimal subtotal, BigDecimal grandTotal,
                                 /* NAV-2: the customer can still accept or decline it */ boolean canRespond) {

    public record LineItem(Long id, String productName, BigDecimal quantity, BigDecimal unitPrice,
                           BigDecimal discountPercent, BigDecimal lineTotal) {}
}
