package com.company.crm.support.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** FR-6.3b: create or replace a ticket auto-assignment rule. No assignUserId = round-robin over all agents. */
@Getter
@Setter
public class TicketAssignmentRuleReqDto {

    @NotBlank
    @Size(max = 100)
    private String name;

    /** Lower runs first; defaults to 100. */
    private Integer priority;

    /** Defaults to true. */
    private Boolean active;

    /** Ticket priority (critical/high/medium/low), or empty for any. */
    private String matchPriority;

    /** Customer industry code, or empty for any. */
    private String matchIndustry;

    /** A service agent of this tenant, or null for round-robin across all active agents. */
    private Long assignUserId;
}
