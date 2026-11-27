package com.company.crm.sales.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpportunityStageReqDto {

    /** One of the OpportunityStage db values, e.g. "proposal", "won", "lost". */
    @NotBlank
    private String stage;

    /** FR-3.4: required when {@code stage} is "lost". */
    private String lossReason;

    /** VAL-7: moving ahead past the next stage is an explicit, logged override. */
    private boolean force = false;

    /** BR-1/VAL-7: why the stages are skipped — kept in the audit trail with the override. */
    @Size(max = 500)
    private String overrideReason;
}
