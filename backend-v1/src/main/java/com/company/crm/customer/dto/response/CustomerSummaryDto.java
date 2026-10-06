package com.company.crm.customer.dto.response;

/** Customer counts within the caller's data scope. */
public record CustomerSummaryDto(
        Long total,
        Long active,
        /** Created since the first day of this quarter. */
        Long newThisQuarter
) {}
