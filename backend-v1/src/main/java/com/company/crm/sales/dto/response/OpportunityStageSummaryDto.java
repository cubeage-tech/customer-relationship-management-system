package com.company.crm.sales.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/** FR-3.3: cumulative deal value per pipeline stage, for the Kanban/pipeline view. */
@Getter
@AllArgsConstructor
public class OpportunityStageSummaryDto {
    private String stage;
    private long count;
    private BigDecimal totalValue;
}
