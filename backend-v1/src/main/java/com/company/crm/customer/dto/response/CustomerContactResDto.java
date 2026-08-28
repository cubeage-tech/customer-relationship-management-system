package com.company.crm.customer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CustomerContactResDto {
    private Long id;
    private String name;
    private String designation;
    private String phone;
    private String email;
}
