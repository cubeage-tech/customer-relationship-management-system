package com.company.crm.common.enums;

/** Who receives an SMS alert: the customer, or the record's internal owner/assignee. */
public enum SmsAlertRecipient {
    CUSTOMER("customer"),
    INTERNAL("internal");

    private final String dbValue;

    SmsAlertRecipient(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static SmsAlertRecipient fromDbValue(String dbValue) {
        for (SmsAlertRecipient value : values()) {
            if (value.dbValue.equals(dbValue)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown SMS alert recipient value: " + dbValue);
    }
}
