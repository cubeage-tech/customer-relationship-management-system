package com.company.crm.common.enums;

public enum PaymentStatus {
    CREATED("created"),
    PAID("paid"),
    FAILED("failed");

    private final String dbValue;

    PaymentStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static PaymentStatus fromDbValue(String dbValue) {
        for (PaymentStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown payment_status value: " + dbValue);
    }
}
