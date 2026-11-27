package com.company.crm.lead.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    /** BR-1/VAL-7: why the stages are skipped — kept in the audit trail with the override. */
    @Size(max = 500)
    private String overrideReason;
}
