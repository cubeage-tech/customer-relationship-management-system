package com.company.crm.common.enums;

/**
 * Lead pipeline stage — see SRS §6.2 "Lead Pipeline (as defined by the client)".
 * Declaration order IS the pipeline order (BR-1: a lead must progress through
 * stages in order unless the transition is explicitly forced).
 */
public enum LeadStage {
    NEW_LEAD("new_lead"),
    CONTACTED("contacted"),
    MEETING("meeting"),
    QUOTATION("quotation"),
    NEGOTIATION("negotiation"),
    CONVERTED("converted");

    private final String dbValue;

    LeadStage(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    /** Position in the pipeline — used to detect stage skips (BR-1). */
    public int getOrder() {
        return ordinal();
    }

    public static LeadStage fromDbValue(String dbValue) {
        for (LeadStage stage : values()) {
            if (stage.dbValue.equals(dbValue)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Unknown lead stage value: " + dbValue);
    }
}
