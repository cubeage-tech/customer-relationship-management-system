package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SmsAlertRecipientConverter implements AttributeConverter<SmsAlertRecipient, String> {

    @Override
    public String convertToDatabaseColumn(SmsAlertRecipient attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public SmsAlertRecipient convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SmsAlertRecipient.fromDbValue(dbData);
    }
}
