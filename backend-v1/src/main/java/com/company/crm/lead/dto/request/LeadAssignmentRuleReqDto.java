package com.company.crm.lead.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** FR-2.3: create or replace an auto-assignment rule. Give exactly one of assignUserId / assignTeamId. */
@Getter
@Setter
public class LeadAssignmentRuleReqDto {

    @NotBlank
    @Size(max = 100)
    private String name;

    /** Lower runs first; defaults to 100. */
    private Integer priority;

    /** Defaults to true. */
    private Boolean active;

    /** Lead source db value, or empty for any source. */
    private String matchSource;

    /** Lead industry db value, or empty for any industry. */
    private String matchIndustry;

    private Long assignUserId;

    private Long assignTeamId;
}
