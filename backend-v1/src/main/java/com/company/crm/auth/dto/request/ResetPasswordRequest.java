package com.company.crm.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank
    private String token;

    @NotBlank
    @Size(max = 128, message = "Password must be at most 128 characters") // SEC-1b: the rest is PasswordPolicy
    private String newPassword;
}
