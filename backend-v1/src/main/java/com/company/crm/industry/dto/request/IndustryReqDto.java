package com.company.crm.industry.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** FR-1.6: add an industry (label required) or rename / (de)activate one (both optional). */
@Getter
@Setter
public class IndustryReqDto {

    @Size(max = 60)
    private String label;

    private Boolean active;
}
