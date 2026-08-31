package com.company.crm.customer.mapper;

import com.company.crm.customer.dto.response.CustomerContactResDto;
import com.company.crm.customer.dto.response.CustomerResDto;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.entity.CustomerContact;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResDto toDto(Customer customer) {
        return new CustomerResDto(
                customer.getId(),
                customer.getCompanyName(),
                customer.getIndustry().getDbValue(),
                customer.getStatus().getDbValue(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getWebsite(),
                customer.getAddress(),
                customer.getNotes(),
                customer.getOwner() != null ? customer.getOwner().getId() : null,
                customer.getOwner() != null ? customer.getOwner().getFullName() : null,
                customer.getContacts().stream().map(this::toContactDto).toList(),
                customer.getCreatedAt()
        );
    }

    public CustomerContactResDto toContactDto(CustomerContact contact) {
        return new CustomerContactResDto(
                contact.getId(),
                contact.getName(),
                contact.getDesignation(),
                contact.getPhone(),
                contact.getEmail()
        );
    }
}
