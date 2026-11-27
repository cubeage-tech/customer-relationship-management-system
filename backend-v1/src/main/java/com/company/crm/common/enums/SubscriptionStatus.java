package com.company.crm.common.enums;

public enum SubscriptionStatus {
    TRIAL("trial"),
    ACTIVE("active"),
    PAST_DUE("past_due"),
    CANCELLED("cancelled"),
    EXPIRED("expired");

    private final String dbValue;

    SubscriptionStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static SubscriptionStatus fromDbValue(String dbValue) {
        for (SubscriptionStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown subscription_status value: " + dbValue);
    }
}
