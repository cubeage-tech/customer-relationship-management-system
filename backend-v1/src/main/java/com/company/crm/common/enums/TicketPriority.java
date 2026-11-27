package com.company.crm.common.enums;

/** Service ticket priority — see SRS FR-6.1. Declaration order is severity order (most urgent first). */
public enum TicketPriority {
    CRITICAL("critical"),
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    private final String dbValue;

    TicketPriority(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static TicketPriority fromDbValue(String dbValue) {
        for (TicketPriority priority : values()) {
            if (priority.dbValue.equals(dbValue)) {
                return priority;
            }
        }
        throw new IllegalArgumentException("Unknown ticket priority value: " + dbValue);
    }
}
