package com.company.crm.quotation.event;

/** NAV-2: the customer accepted (or declined) a quotation from its view link. */
public record QuotationRespondedEvent(Long tenantId, Long quotationId, boolean accepted) {}
