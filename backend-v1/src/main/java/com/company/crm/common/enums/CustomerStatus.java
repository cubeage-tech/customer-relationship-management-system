package com.company.crm.common.enums;

/** BR-8: a customer with financial history is archived, never deleted. */
public enum CustomerStatus {
    ACTIVE("active"),
    ARCHIVED("archived");

    private final String dbValue;

    CustomerStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CustomerStatus fromDbValue(String dbValue) {
        for (CustomerStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown customer_status value: " + dbValue);
    }
}
