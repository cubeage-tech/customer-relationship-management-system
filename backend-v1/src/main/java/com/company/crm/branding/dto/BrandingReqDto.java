package com.company.crm.branding.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** AC-4.2: company details printed on quotation PDFs. Blank clears a field. */
@Getter
@Setter
public class BrandingReqDto {

    @Size(max = 300)
    private String address;

    @Size(max = 30)
    private String phone;

    @Email
    @Size(max = 150)
    private String email;

    /** GSTIN or other tax registration number. */
    @Size(max = 30)
    private String taxId;

    @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$", message = "Brand colour must look like #4f46e5")
    private String color;
}
