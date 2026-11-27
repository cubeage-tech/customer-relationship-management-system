package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OpportunityStageConverter implements AttributeConverter<OpportunityStage, String> {

    @Override
    public String convertToDatabaseColumn(OpportunityStage attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public OpportunityStage convertToEntityAttribute(String dbData) {
        return dbData == null ? null : OpportunityStage.fromDbValue(dbData);
    }
}
