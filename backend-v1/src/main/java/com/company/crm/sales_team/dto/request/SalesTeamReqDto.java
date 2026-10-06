package com.company.crm.sales_team.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Create or update a sales team. */
@Getter
@Setter
public class SalesTeamReqDto {

    @NotBlank
    @Size(max = 100)
    private String name;

    /** A sales_manager of this tenant; null leaves the team without a manager. */
    private Long managerId;
}
