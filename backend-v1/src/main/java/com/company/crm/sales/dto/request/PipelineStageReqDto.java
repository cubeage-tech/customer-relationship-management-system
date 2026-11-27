package com.company.crm.sales.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** FR-3.2: add a stage (label required) or rename / (de)activate one (both optional). */
@Getter
@Setter
public class PipelineStageReqDto {

    @Size(max = 60)
    private String label;

    private Boolean active;
}
