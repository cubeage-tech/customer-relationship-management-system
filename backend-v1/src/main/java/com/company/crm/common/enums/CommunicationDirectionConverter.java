package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CommunicationDirectionConverter implements AttributeConverter<CommunicationDirection, String> {

    @Override
    public String convertToDatabaseColumn(CommunicationDirection direction) {
        return direction == null ? null : direction.getDbValue();
    }

    @Override
    public CommunicationDirection convertToEntityAttribute(String value) {
        return value == null ? null : CommunicationDirection.fromDbValue(value);
    }
}
