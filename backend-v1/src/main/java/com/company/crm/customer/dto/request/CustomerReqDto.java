package com.company.crm.customer.dto.request;

import com.company.crm.common.validation.ValidPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerReqDto {

    @NotBlank
    @Size(min = 2, max = 255, message = "Company name must be 2 to 255 characters") // VAL-3
    private String companyName;

    /** FR-1.6: an active code from the tenant's industry list (GET /api/industries), e.g. "manufacturing". */
    @NotBlank
    private String industry;

    @Email
    private String email;

    @ValidPhone // VAL-2
    private String phone;

    private String website;

    private String address;

    private String notes;

    /** Salesperson this account is assigned to. Defaults to the creator when omitted. */
    private Long ownerId;
}
