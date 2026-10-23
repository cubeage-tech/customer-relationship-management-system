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
    /** FR-6.1: the asset the ticket is about, if any. */
    private Long assetId;
    private String assetName;
    /** FR-6.1: "staff" or "customer" (public support form) and the reporter's address. */
    private String source;
    private String reporterEmail;
    /** FR-6.5: when the feedback was given. */
    private LocalDateTime feedbackAt;
}
