package com.company.crm.customer.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerReqDto {

    @NotBlank
    private String companyName;

    /** FR-1.6: an active code from the tenant's industry list (GET /api/industries), e.g. "manufacturing". */
    @NotBlank
    private String industry;

    @Email
    private String email;

    private String phone;

    private String website;

    private String address;

    private String notes;

    /** Salesperson this account is assigned to. Defaults to the creator when omitted. */
    private Long ownerId;
}
