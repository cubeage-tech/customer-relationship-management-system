package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TicketPriorityConverter implements AttributeConverter<TicketPriority, String> {

    @Override
    public String convertToDatabaseColumn(TicketPriority attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public TicketPriority convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TicketPriority.fromDbValue(dbData);
    }
}
