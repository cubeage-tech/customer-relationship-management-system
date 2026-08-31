package com.company.crm.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignChannelConverter implements AttributeConverter<CampaignChannel, String> {

    @Override
    public String convertToDatabaseColumn(CampaignChannel attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public CampaignChannel convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CampaignChannel.fromDbValue(dbData);
    }
}
