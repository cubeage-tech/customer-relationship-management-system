package com.company.crm.industry.dto.response;

/** FR-1.6: one industry of the tenant's customer list. */
public record IndustryResDto(String code, String label, boolean active, int sortOrder) {}
