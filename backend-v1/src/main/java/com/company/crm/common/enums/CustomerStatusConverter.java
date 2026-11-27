package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CustomerStatusConverter implements AttributeConverter<CustomerStatus, String> {

    @Override
    public String convertToDatabaseColumn(CustomerStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public CustomerStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CustomerStatus.fromDbValue(dbData);
    }
}
