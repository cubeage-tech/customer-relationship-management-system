package com.company.crm.support.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** FR-6.4: counts of open tickets by SLA status, for the ticket list header/escalation view. */
@Getter
@AllArgsConstructor
public class ServiceTicketSummaryDto {
    private long onTrack;
    private long atRisk;
    private long breached;
}
