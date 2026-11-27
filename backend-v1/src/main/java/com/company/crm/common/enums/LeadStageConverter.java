package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LeadStageConverter implements AttributeConverter<LeadStage, String> {

    @Override
    public String convertToDatabaseColumn(LeadStage attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public LeadStage convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LeadStage.fromDbValue(dbData);
    }
}
