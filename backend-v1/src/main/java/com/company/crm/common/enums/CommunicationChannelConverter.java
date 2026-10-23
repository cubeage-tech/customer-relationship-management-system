package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CommunicationChannelConverter implements AttributeConverter<CommunicationChannel, String> {

    @Override
    public String convertToDatabaseColumn(CommunicationChannel attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public CommunicationChannel convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CommunicationChannel.fromDbValue(dbData);
    }
}
