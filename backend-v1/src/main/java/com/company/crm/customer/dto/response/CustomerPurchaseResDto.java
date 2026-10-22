package com.company.crm.customer.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** FR-1.3: one entry in a customer's purchase history (a won deal or a customer-approved quotation). */
public record CustomerPurchaseResDto(
        /** "opportunity" or "quotation". */
        String source,
        Long sourceId,
        /** Quotation number for quotations; null for deals. */
        String reference,
        String productService,
        BigDecimal orderValue,
        LocalDateTime purchasedAt
) {}
