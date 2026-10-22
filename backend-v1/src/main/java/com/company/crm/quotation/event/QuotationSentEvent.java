package com.company.crm.quotation.event;

/** Published when a quotation is sent to the customer (consumed by FR-5.3 SMS alerts after commit). */
public record QuotationSentEvent(Long tenantId, Long quotationId) {}
