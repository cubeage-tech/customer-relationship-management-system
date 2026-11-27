package com.company.crm.communication.dto.request;

import com.company.crm.common.validation.ValidPhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** API-G06 / FR-5.3: an SMS sent to a customer or lead from inside the CRM. */
@Getter
@Setter
public class SmsSendReqDto {

    /** One of customerId / leadId is required. */
    private Long customerId;

    private Long leadId;

    /** Phone number; a 10-digit number gets the default country code. */
    @NotBlank
    @Size(max = 30)
    @ValidPhone // VAL-2
    private String to;

    /** SMS text; long messages are split into parts by the provider. */
    @NotBlank
    @Size(max = 1600)
    private String body;

    /** FR-AI-5.2: when the text comes from an AI draft — its id, and the user's confirmation they reviewed it. */
    private Long aiDraftId;

    private boolean aiReviewed;
}
