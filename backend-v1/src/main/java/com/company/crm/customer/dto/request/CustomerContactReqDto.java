package com.company.crm.customer.dto.request;

import com.company.crm.common.validation.ValidPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerContactReqDto {

    @NotBlank
    private String name;

    private String designation;

    @ValidPhone // VAL-2
    private String phone;

    @Email
    private String email;
}
