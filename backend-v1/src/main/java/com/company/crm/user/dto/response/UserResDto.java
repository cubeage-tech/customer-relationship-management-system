package com.company.crm.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserResDto {
    private Long id;
    private Long tenantId;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private String status;
    private boolean emailVerified;
    private LocalDateTime createdAt;
}
