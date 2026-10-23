package com.company.crm.branding.dto;

/** AC-4.2: the tenant's quotation branding; {@code hasLogo} says whether GET /api/branding/logo has an image. */
public record BrandingResDto(String companyName, String address, String phone, String email,
                             String taxId, String color, boolean hasLogo) {}
