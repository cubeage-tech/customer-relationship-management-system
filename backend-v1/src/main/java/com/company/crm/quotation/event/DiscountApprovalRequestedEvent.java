package com.company.crm.quotation.event;

/** NOT-5: a quotation's discount now needs approval (consumed after commit to alert the approvers). */
public record DiscountApprovalRequestedEvent(Long tenantId, Long quotationId, Long requestedById) {}
