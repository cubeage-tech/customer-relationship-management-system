package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentStatusConverter implements AttributeConverter<PaymentStatus, String> {

    @Override
    public String convertToDatabaseColumn(PaymentStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public PaymentStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PaymentStatus.fromDbValue(dbData);
    }
}
