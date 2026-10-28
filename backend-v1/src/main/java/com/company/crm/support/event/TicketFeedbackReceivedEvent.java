package com.company.crm.support.event;

/** FR-AI-6.1: a customer's feedback (rating and comment) was recorded on a ticket. */
public record TicketFeedbackReceivedEvent(Long tenantId, Long ticketId) {}
