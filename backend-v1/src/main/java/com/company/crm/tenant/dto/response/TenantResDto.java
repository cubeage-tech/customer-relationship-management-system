package com.company.crm.tenant.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class TenantResDto {
    private Long id;
    private String companyName;
    private String legalName;
    private String plan;
    private String status;
    private LocalDateTime createdAt;
}
