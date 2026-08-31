package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LeadSourceConverter implements AttributeConverter<LeadSource, String> {

    @Override
    public String convertToDatabaseColumn(LeadSource attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public LeadSource convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LeadSource.fromDbValue(dbData);
    }
}
