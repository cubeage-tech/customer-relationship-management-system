package com.company.crm.support.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceTicketAssignReqDto {

    @NotNull
    private Long technicianId;
}
