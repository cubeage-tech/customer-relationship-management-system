package com.company.crm.communication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CommunicationLogReqDto {

    @NotBlank
    private String channel;

    @NotBlank
    private String direction;

    @Size(max = 255)
    private String subject;

    @NotBlank
    @Size(max = 10000)
    private String content;

    @Size(max = 150)
    private String contactName;

    @Size(max = 255)
    private String contactAddress;

    @Size(max = 20)
    private String status;

    private LocalDateTime occurredAt;
}
