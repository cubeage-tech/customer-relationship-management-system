package com.company.crm.support.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ServiceTicketResDto {
    private Long id;
    private Long customerId;
    private String customerName;
    private String subject;
    private String description;
    private String priority;
    private String status;
    private LocalDateTime slaDueAt;
    /** on_track | at_risk | breached | met — derived, never persisted (see FR-6.4). */
    private String slaStatus;
    private LocalDateTime resolvedAt;
    private Integer feedbackScore;
    private String feedbackComment;
    private Long technicianId;
    private String technicianName;
    private LocalDateTime createdAt;
}
