package com.company.crm.communication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** FR-5.4: a manually logged call against a customer or lead. */
@Getter
@Setter
public class CallLogReqDto {

    /** One of customerId / leadId is required. */
    private Long customerId;

    private Long leadId;

    /** inbound | outbound */
    @NotBlank
    private String direction;

    /** connected | no_answer | busy | voicemail | wrong_number */
    @NotBlank
    private String outcome;

    @PositiveOrZero
    private Integer durationSeconds;

    @Size(max = 5000)
    private String notes;

    /** Phone number called / calling (optional). */
    @Size(max = 30)
    private String phone;

    /** When the call happened; defaults to now. */
    private LocalDateTime occurredAt;
}
