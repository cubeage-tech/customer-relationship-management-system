package com.company.crm.common.enums;

public enum SubscriptionPlan {
    STARTER("starter"),
    BUSINESS("business"),
    ENTERPRISE("enterprise");

    private final String dbValue;

    SubscriptionPlan(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static SubscriptionPlan fromDbValue(String dbValue) {
        for (SubscriptionPlan plan : values()) {
            if (plan.dbValue.equals(dbValue)) {
                return plan;
            }
        }
        throw new IllegalArgumentException("Unknown subscription_plan value: " + dbValue);
    }
}
