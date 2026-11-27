package com.company.crm.communication.event;

/** An inbound email, WhatsApp or SMS was logged (NOT-7 opt-out keywords, NOT-5 AI alerts react after commit). */
public record InboundMessageRecordedEvent(Long tenantId, Long communicationId) {}
