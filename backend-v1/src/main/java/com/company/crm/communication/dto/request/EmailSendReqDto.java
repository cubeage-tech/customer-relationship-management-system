package com.company.crm.communication.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** FR-5.1: an email sent to a customer or lead from inside the CRM. */
@Getter
@Setter
public class EmailSendReqDto {

    /** One of customerId / leadId is required. */
    private Long customerId;

    private Long leadId;

    @NotBlank
    @Email
    private String to;

    @NotBlank
    @Size(max = 255)
    private String subject;

    @NotBlank
    @Size(max = 20000)
    private String body;

    /** FR-AI-5.2: when the text comes from an AI draft — its id, and the user's confirmation they reviewed it. */
    private Long aiDraftId;

    private boolean aiReviewed;
}
