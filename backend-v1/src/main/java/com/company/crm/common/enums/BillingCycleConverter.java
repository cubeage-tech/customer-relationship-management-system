package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BillingCycleConverter implements AttributeConverter<BillingCycle, String> {

    @Override
    public String convertToDatabaseColumn(BillingCycle attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public BillingCycle convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BillingCycle.fromDbValue(dbData);
    }
}
