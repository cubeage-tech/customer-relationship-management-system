package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CommunicationDirectionConverter implements AttributeConverter<CommunicationDirection, String> {

    @Override
    public String convertToDatabaseColumn(CommunicationDirection attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public CommunicationDirection convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CommunicationDirection.fromDbValue(dbData);
    }
}
