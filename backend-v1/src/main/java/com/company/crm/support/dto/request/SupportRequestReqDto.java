package com.company.crm.support.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** FR-6.1: what a customer submits on the public "Report an issue" form. */
@Getter
@Setter
public class SupportRequestReqDto {

    /** Must be the customer's (or one of its contacts') address on record. */
    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @NotBlank
    @Size(max = 200)
    private String subject;

    @Size(max = 5000)
    private String description;

    /** low | medium | high | critical */
    @NotBlank
    private String priority;

    /** Optional: the serial number of the affected asset, if the customer knows it. */
    @Size(max = 100)
    private String serialNumber;
}
