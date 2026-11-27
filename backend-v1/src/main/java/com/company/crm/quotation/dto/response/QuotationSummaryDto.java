package com.company.crm.quotation.dto.response;

import java.math.BigDecimal;

/** Quotation pipeline and discount figures within the caller's data scope. */
public record QuotationSummaryDto(
        long draft,
        /** Sent to the customer and awaiting their answer (pending or viewed). */
        long sent,
        /** Discount above the threshold, waiting for approval. */
        long pendingApproval,
        long approvedCount,
        /** Grand total (after line discounts) of quotations whose discount was approved. */
        BigDecimal approvedTotal,
        /**
         * Value-weighted average discount of approved quotations: total discount ÷ total gross × 100.
         * Null when nothing has been approved yet.
         */
        BigDecimal averageDiscountPercent
) {}
