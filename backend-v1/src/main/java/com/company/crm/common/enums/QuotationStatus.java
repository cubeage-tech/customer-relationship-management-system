package com.company.crm.common.enums;

/** Customer's response to a quotation — see SRS FR-4.5. */
public enum QuotationStatus {
    DRAFT("draft"),
    PENDING("pending"),
    VIEWED("viewed"),
    APPROVED("approved"),
    REJECTED("rejected"),
    EXPIRED("expired");

    private final String dbValue;

    QuotationStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static QuotationStatus fromDbValue(String dbValue) {
        for (QuotationStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown quotation status value: " + dbValue);
    }
}
