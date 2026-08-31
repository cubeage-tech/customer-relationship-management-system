package com.company.crm.common.enums;

/** Channel a marketing campaign runs on — see SRS §6.7. */
public enum CampaignChannel {
    EMAIL("email"),
    SOCIAL_MEDIA("social_media"),
    SMS("sms"),
    EVENT("event"),
    WEBINAR("webinar"),
    PAID_ADS("paid_ads"),
    OTHER("other");

    private final String dbValue;

    CampaignChannel(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CampaignChannel fromDbValue(String dbValue) {
        for (CampaignChannel channel : values()) {
            if (channel.dbValue.equals(dbValue)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("Unknown campaign channel value: " + dbValue);
    }
}
