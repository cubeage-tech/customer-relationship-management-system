package com.company.crm.lead.dto.request;

import lombok.Getter;
import lombok.Setter;

/** FR-2.3: assign a lead to a sales team; null removes the team assignment. */
@Getter
@Setter
public class LeadTeamReqDto {

    private Long teamId;
}
