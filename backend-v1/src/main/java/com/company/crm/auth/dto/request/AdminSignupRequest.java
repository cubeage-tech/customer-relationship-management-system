package com.company.crm.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Public self-service signup — always creates a new tenant + its admin (owner) user. */
@Getter
@Setter
public class AdminSignupRequest {

    /** Present for forward-compat with the frontend payload; must be "admin" or omitted. */
    private String role;

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank
    private String organizationName;

    // Same shape the frontend validates client-side (validation.js BANK_ACCOUNT_NUMBER).
    @NotBlank
    @Pattern(regexp = "\\d{9,18}", message = "Bank account number must be 9-18 digits")
    private String bankAccountNumber;

    private String website;

    private String address;
}
