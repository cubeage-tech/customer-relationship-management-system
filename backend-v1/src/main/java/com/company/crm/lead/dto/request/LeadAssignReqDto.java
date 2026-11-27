package com.company.crm.lead.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadAssignReqDto {

    @NotNull
    private Long ownerId;
}
