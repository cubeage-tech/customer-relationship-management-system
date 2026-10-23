package com.company.crm.meeting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** FR-5.5: schedule or reschedule a meeting with a customer (customerId) or a lead (leadId). */
@Getter
@Setter
public class MeetingReqDto {

    private Long customerId;

    private Long leadId;

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotNull
    private LocalDateTime startsAt;

    @NotNull
    private LocalDateTime endsAt;

    @Size(max = 300)
    private String location;

    private String notes;

    /** FR-5.5b: minutes before the start to send a reminder; null = no reminder. Max one week. */
    @Min(1)
    @Max(10080)
    private Integer reminderMinutes;

    /** FR-5.5b: also send the invite, changes and reminder to the customer/lead contact. */
    private boolean includeCustomer;
}
