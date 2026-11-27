package com.company.crm.lead.dto.response;

/** Lead counts within the caller's data scope (own / team / tenant). */
public record LeadSummaryDto(
        /** All leads in scope. */
        Long total,
        /** Not yet converted. */
        Long open,
        /** Created since the first day of this month. */
        Long newThisMonth
) {}
