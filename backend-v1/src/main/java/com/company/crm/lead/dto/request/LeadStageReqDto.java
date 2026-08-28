package com.company.crm.lead.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadStageReqDto {

    /** One of the LeadStage db values, e.g. "contacted", "converted". */
    @NotBlank
    private String stage;

    /** BR-1: stages may be skipped only via an explicit, logged override. */
    private boolean force = false;
}
