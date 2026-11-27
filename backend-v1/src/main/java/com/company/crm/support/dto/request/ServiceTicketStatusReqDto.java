package com.company.crm.support.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceTicketStatusReqDto {

    /** One of: open | assigned | in_progress | resolved | closed */
    @NotBlank
    private String status;
}
