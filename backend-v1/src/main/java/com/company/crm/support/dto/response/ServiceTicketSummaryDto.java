package com.company.crm.support.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * FR-6.4: counts of open tickets by SLA status, for the ticket list header/escalation view,
 * plus dashboard figures. Service agents get their assigned tickets only; other roles the tenant.
 * The last four fields were added for the dashboards — the first three are unchanged.
 */
@Getter
@AllArgsConstructor
public class ServiceTicketSummaryDto {
    private long onTrack;
    private long atRisk;
    private long breached;

    /** Open = open, assigned or in progress. */
    private long open;
    /** Open and not yet breached, with the SLA deadline in the next 24 hours. */
    private long dueWithin24h;
    /** Resolved since Monday 00:00. */
    private long resolvedThisWeek;
    /** Average hours from creation to resolution over the last 30 days; null when none resolved. */
    private Double averageResolutionHours;
}
