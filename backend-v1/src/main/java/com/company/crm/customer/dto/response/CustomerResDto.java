package com.company.crm.customer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class CustomerResDto {
    private Long id;
    private String companyName;
    private String industry;
    private String status;
    private String email;
    private String phone;
    private String website;
    private String address;
    private String notes;
    private Long ownerId;
    private String ownerName;
    private List<CustomerContactResDto> contacts;
    private LocalDateTime createdAt;
}
