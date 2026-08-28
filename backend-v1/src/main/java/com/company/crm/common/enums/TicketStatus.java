package com.company.crm.common.enums;

/** Service ticket lifecycle — see SRS FR-6.2. */
public enum TicketStatus {
    OPEN("open"),
    ASSIGNED("assigned"),
    IN_PROGRESS("in_progress"),
    RESOLVED("resolved"),
    CLOSED("closed");

    private final String dbValue;

    TicketStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static TicketStatus fromDbValue(String dbValue) {
        for (TicketStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ticket status value: " + dbValue);
    }
}
