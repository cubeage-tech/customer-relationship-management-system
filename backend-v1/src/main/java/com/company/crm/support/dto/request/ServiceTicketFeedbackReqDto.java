package com.company.crm.support.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceTicketFeedbackReqDto {

    @NotNull
    @Min(1)
    @Max(5)
    private Integer score;

    private String comment;
}
