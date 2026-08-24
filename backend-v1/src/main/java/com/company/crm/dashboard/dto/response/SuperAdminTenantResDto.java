package com.company.crm.dashboard.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SuperAdminTenantResDto {
    private Long id;
    private String companyName;
    private String legalName;
    private String plan;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
