package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SubscriptionPlanConverter implements AttributeConverter<SubscriptionPlan, String> {

    @Override
    public String convertToDatabaseColumn(SubscriptionPlan attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public SubscriptionPlan convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SubscriptionPlan.fromDbValue(dbData);
    }
}
