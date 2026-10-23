package com.company.crm.sales.dto.response;

/** FR-3.2: one stage of the tenant's pipeline; {@code terminal} = Won or Lost (fixed, closes the deal). */
public record PipelineStageResDto(String code, String label, int sortOrder, boolean active, boolean terminal) {}
