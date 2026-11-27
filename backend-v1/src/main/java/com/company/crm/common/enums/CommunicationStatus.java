package com.company.crm.common.enums;

/** Delivery state of a logged communication; calls are simply LOGGED, inbound messages RECEIVED. */
public enum CommunicationStatus {
    LOGGED("logged"),
    QUEUED("queued"),
    SENT("sent"),
    DELIVERED("delivered"),
    READ("read"),
    FAILED("failed"),
    RECEIVED("received");

    private final String dbValue;

    CommunicationStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CommunicationStatus fromDbValue(String dbValue) {
        for (CommunicationStatus value : values()) {
            if (value.dbValue.equals(dbValue)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown communication status value: " + dbValue);
    }
}
