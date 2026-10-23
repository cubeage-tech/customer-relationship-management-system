package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CallOutcomeConverter implements AttributeConverter<CallOutcome, String> {

    @Override
    public String convertToDatabaseColumn(CallOutcome attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public CallOutcome convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CallOutcome.fromDbValue(dbData);
    }
}
