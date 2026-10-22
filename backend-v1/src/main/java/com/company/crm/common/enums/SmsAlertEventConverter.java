package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SmsAlertEventConverter implements AttributeConverter<SmsAlertEvent, String> {

    @Override
    public String convertToDatabaseColumn(SmsAlertEvent attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public SmsAlertEvent convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SmsAlertEvent.fromDbValue(dbData);
    }
}
