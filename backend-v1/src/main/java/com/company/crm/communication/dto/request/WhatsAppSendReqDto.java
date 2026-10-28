package com.company.crm.communication.dto.request;

import com.company.crm.common.validation.ValidPhone;
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
    @ValidPhone // VAL-2
    private String to;

    @NotBlank
    @Size(max = 1600)
    private String body;

    /** FR-AI-5.2: when the text comes from an AI draft — its id, and the user's confirmation they reviewed it. */
    private Long aiDraftId;

    private boolean aiReviewed;
}
