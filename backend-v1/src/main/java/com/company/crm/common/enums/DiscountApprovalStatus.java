package com.company.crm.common.enums;

/** BR-3/FR-4.3: internal approval state for a quotation's discount, distinct from the customer-facing status. */
public enum DiscountApprovalStatus {
    NOT_REQUIRED("not_required"),
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String dbValue;

    DiscountApprovalStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static DiscountApprovalStatus fromDbValue(String dbValue) {
        for (DiscountApprovalStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown discount approval status value: " + dbValue);
    }
}
