package com.company.crm.lead.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LeadReqDto {

    @NotBlank
    private String leadName;

    @NotBlank
    private String companyName;

    @Email
    private String contactEmail;

    private String contactPhone;

    /** One of the IndustryType db values, e.g. "manufacturing", "real_estate". */
    @NotBlank
    private String industry;

    /** One of: website | referral | exhibition | cold_call | marketing_campaign | other */
    @NotBlank
    private String source;

    private LocalDateTime followUpDate;

    private String notes;

    /** Salesperson this lead is assigned to. Defaults to the creator when omitted. */
    private Long ownerId;
}
