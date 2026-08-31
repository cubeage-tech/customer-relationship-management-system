package com.company.crm.support.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceTicketReqDto {

    @NotNull
    private Long customerId;

    @NotBlank
    private String subject;

    private String description;

    /** One of: low | medium | high | critical */
    @NotBlank
    private String priority;
}
