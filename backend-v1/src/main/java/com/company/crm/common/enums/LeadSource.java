package com.company.crm.common.enums;

/** Where a lead came from — see SRS FR-2.2. */
public enum LeadSource {
    WEBSITE("website"),
    REFERRAL("referral"),
    EXHIBITION("exhibition"),
    COLD_CALL("cold_call"),
    MARKETING_CAMPAIGN("marketing_campaign"),
    OTHER("other");

    private final String dbValue;

    LeadSource(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static LeadSource fromDbValue(String dbValue) {
        for (LeadSource source : values()) {
            if (source.dbValue.equals(dbValue)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown lead source value: " + dbValue);
    }
}
