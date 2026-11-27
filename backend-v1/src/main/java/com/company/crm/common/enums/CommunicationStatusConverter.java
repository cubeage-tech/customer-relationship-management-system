package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CommunicationStatusConverter implements AttributeConverter<CommunicationStatus, String> {

    @Override
    public String convertToDatabaseColumn(CommunicationStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public CommunicationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CommunicationStatus.fromDbValue(dbData);
    }
}
