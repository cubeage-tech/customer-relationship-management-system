package com.company.crm.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSummaryResDto {
    private Long id;
    private String name;
    private String email;
    private String role;
    private Long tenantId;
}
