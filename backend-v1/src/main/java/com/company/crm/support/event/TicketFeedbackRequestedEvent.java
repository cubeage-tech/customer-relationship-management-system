package com.company.crm.support.event;

/** FR-6.5: a ticket was resolved for the first time — its customer is sent a feedback link after commit. */
public record TicketFeedbackRequestedEvent(Long tenantId, Long ticketId) {}
