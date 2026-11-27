package com.company.crm.common.enums;

/** Campaign lifecycle — see SRS §6.7. */
public enum CampaignStatus {
    DRAFT("draft"),
    /** FR-7.1b: launches itself at scheduledAt. */
    SCHEDULED("scheduled"),
    ACTIVE("active"),
    PAUSED("paused"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String dbValue;

    CampaignStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CampaignStatus fromDbValue(String dbValue) {
        for (CampaignStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown campaign status value: " + dbValue);
    }
}
