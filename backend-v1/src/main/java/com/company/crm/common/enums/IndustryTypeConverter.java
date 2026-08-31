package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class IndustryTypeConverter implements AttributeConverter<IndustryType, String> {

    @Override
    public String convertToDatabaseColumn(IndustryType attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public IndustryType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : IndustryType.fromDbValue(dbData);
    }
}
