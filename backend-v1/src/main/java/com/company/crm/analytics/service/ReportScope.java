package com.company.crm.analytics.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * FR-9.6: the filters a report runs with, already narrowed to what the caller may see.
 *
 * @param ownerIds the record owners included; null = the whole tenant
 * @param industry customer (or lead) industry code, or null for all
 * @param from     first day of the period (inclusive)
 * @param to       last day of the period (inclusive)
 */
public record ReportScope(Long tenantId, Set<Long> ownerIds, String industry, LocalDate from, LocalDate to) {

    public LocalDateTime start() {
        return from.atStartOfDay();
    }

    /** Exclusive end: the day after {@link #to}. */
    public LocalDateTime end() {
        return to.plusDays(1).atStartOfDay();
    }

    /** The same-length period just before this one (for growth). */
    public ReportScope previous() {
        long days = to.toEpochDay() - from.toEpochDay() + 1;
        return new ReportScope(tenantId, ownerIds, industry, from.minusDays(days), from.minusDays(1));
    }
}
