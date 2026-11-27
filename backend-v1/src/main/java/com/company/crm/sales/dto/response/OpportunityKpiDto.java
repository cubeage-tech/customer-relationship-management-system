package com.company.crm.sales.dto.response;

import java.math.BigDecimal;

/** Pipeline KPIs within the caller's data scope. "Open" = any stage except won/lost. */
public record OpportunityKpiDto(
        Long openCount,
        /** Sum of deal values of open opportunities. */
        BigDecimal openValue,
        /** Open opportunities expected to close Monday–Sunday of this week. */
        Long closingThisWeek,
        /** Moved to won since the 1st of this month. */
        Long wonThisMonthCount,
        BigDecimal wonThisMonthValue,
        /** Value moved to won since the start of this quarter. */
        BigDecimal wonThisQuarterValue
) {}
