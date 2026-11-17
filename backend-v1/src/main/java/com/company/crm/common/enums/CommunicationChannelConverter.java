package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CommunicationChannelConverter implements AttributeConverter<CommunicationChannel, String> {

    @Override
    public String convertToDatabaseColumn(CommunicationChannel channel) {
        return channel == null ? null : channel.getDbValue();
    }

    @Override
    public CommunicationChannel convertToEntityAttribute(String value) {
        return value == null ? null : CommunicationChannel.fromDbValue(value);
    }
}
