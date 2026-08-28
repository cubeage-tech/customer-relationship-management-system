package com.company.crm.sales.dto.request;

import jakarta.validation.constraints.NotBlank;
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
}
