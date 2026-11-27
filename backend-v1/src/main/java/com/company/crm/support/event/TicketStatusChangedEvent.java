package com.company.crm.support.event;

import com.company.crm.common.enums.TicketStatus;

/** Published when a service ticket moves to a different status (consumed by FR-5.3 SMS alerts after commit). */
public record TicketStatusChangedEvent(Long tenantId, Long ticketId, TicketStatus status) {}
