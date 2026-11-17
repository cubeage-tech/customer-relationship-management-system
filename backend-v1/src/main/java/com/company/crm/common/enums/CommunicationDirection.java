package com.company.crm.common.enums;

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
        for (CommunicationDirection direction : values()) {
            if (direction.dbValue.equalsIgnoreCase(dbValue)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Unknown communication direction: " + dbValue);
    }
}
