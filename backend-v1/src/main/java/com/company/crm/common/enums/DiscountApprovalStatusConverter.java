package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DiscountApprovalStatusConverter implements AttributeConverter<DiscountApprovalStatus, String> {

    @Override
    public String convertToDatabaseColumn(DiscountApprovalStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public DiscountApprovalStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DiscountApprovalStatus.fromDbValue(dbData);
    }
}
