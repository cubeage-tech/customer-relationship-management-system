package com.company.crm.common.enums;

/** Whether we contacted the customer or they contacted us. */
public enum CommunicationDirection {
    INBOUND("inbound"),
    OUTBOUND("outbound");

    private final String dbValue;

    CommunicationDirection(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CommunicationDirection fromDbValue(String dbValue) {
        for (CommunicationDirection value : values()) {
            if (value.dbValue.equals(dbValue)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown communication direction value: " + dbValue);
    }
}
