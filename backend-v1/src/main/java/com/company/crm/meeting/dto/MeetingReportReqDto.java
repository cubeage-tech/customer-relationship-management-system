package com.company.crm.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** FR-M.3: the report filed after a meeting. Sending it again replaces the earlier report. */
@Getter
@Setter
public class MeetingReportReqDto {

    /** positive | neutral | negative | no_show */
    @NotBlank
    private String outcome;

    @NotBlank
    @Size(max = 5000)
    private String notes;

    @Size(max = 2000)
    private String nextSteps;
}
