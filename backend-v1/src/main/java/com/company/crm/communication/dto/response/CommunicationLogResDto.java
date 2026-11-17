package com.company.crm.communication.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CommunicationLogResDto {
    private Long id;
    private String channel;
    private String direction;
    private String subject;
    private String content;
    private String contactName;
    private String contactAddress;
    private String status;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private Long createdById;
    private String createdByName;
}
