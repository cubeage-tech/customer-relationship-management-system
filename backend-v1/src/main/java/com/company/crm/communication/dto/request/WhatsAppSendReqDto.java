package com.company.crm.communication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** FR-5.2: a WhatsApp message sent to a customer or lead from inside the CRM. */
@Getter
@Setter
public class WhatsAppSendReqDto {

    /** One of customerId / leadId is required. */
    private Long customerId;

    private Long leadId;

    /** Phone number; a 10-digit number gets the default country code. */
    @NotBlank
    @Size(max = 30)
    private String to;

    @NotBlank
    @Size(max = 1600)
    private String body;
}
