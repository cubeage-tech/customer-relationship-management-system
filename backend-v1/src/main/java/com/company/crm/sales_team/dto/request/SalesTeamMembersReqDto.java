package com.company.crm.sales_team.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** The complete roster of a team — replaces the current members. Users move out of any previous team. */
@Getter
@Setter
public class SalesTeamMembersReqDto {

    @NotNull
    @Size(max = 500)
    private List<@NotNull Long> userIds;
}
