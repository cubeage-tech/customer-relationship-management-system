package com.company.crm.customer.dto.request;

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

    private String phone;

    @Email
    private String email;
}
