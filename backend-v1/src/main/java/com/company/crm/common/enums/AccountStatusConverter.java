package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AccountStatusConverter implements AttributeConverter<AccountStatus, String> {

    @Override
    public String convertToDatabaseColumn(AccountStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public AccountStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AccountStatus.fromDbValue(dbData);
    }
}
