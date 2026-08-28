package com.company.crm.lead.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class LeadResDto {
    private Long id;
    private String leadName;
    private String companyName;
    private String contactEmail;
    private String contactPhone;
    private String industry;
    private String source;
    private String stage;
    private LocalDateTime followUpDate;
    private String notes;
    private Long ownerId;
    private String ownerName;
    private Long convertedCustomerId;
    private LocalDateTime createdAt;
}
