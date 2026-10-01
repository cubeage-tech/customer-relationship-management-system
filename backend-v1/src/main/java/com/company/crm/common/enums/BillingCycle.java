package com.company.crm.common.enums;

public enum BillingCycle {
    MONTHLY("monthly"),
    ANNUAL("annual");

    private final String dbValue;

    BillingCycle(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static BillingCycle fromDbValue(String dbValue) {
        for (BillingCycle cycle : values()) {
            if (cycle.dbValue.equals(dbValue)) {
                return cycle;
            }
        }
        throw new IllegalArgumentException("Unknown billing_cycle value: " + dbValue);
    }
}
