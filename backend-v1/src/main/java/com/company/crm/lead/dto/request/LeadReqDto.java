package com.company.crm.lead.dto.request;

import com.company.crm.common.validation.ValidPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LeadReqDto {

    @NotBlank
    private String leadName;

    @NotBlank
    @Size(min = 2, max = 255, message = "Company name must be 2 to 255 characters") // VAL-3
    private String companyName;

    @Email
    private String contactEmail;

    @ValidPhone // VAL-2
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

    /** FR-5.3: the marketing campaign this lead came from, if any. */
    private Long campaignId;
}
