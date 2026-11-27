package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class QuotationStatusConverter implements AttributeConverter<QuotationStatus, String> {

    @Override
    public String convertToDatabaseColumn(QuotationStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public QuotationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : QuotationStatus.fromDbValue(dbData);
    }
}
