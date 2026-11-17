package com.company.crm.common.enums;

public enum CommunicationChannel {
    EMAIL("email"),
    WHATSAPP("whatsapp"),
    SMS("sms"),
    CALL("call");

    private final String dbValue;

    CommunicationChannel(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CommunicationChannel fromDbValue(String dbValue) {
        for (CommunicationChannel channel : values()) {
            if (channel.dbValue.equalsIgnoreCase(dbValue)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("Unknown communication channel: " + dbValue);
    }
}
