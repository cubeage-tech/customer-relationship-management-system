package com.company.crm.common.enums;

/** Communication channel of a logged interaction (Module 5, DB-E09). */
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
        for (CommunicationChannel value : values()) {
            if (value.dbValue.equals(dbValue)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown communication channel value: " + dbValue);
    }
}
