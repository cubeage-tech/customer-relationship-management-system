package com.company.crm.common.enums;

/** Sales pipeline stage — see SRS FR-3.2. */
public enum OpportunityStage {
    QUALIFICATION("qualification"),
    PROPOSAL("proposal"),
    NEGOTIATION("negotiation"),
    WON("won"),
    LOST("lost");

    private final String dbValue;

    OpportunityStage(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static OpportunityStage fromDbValue(String dbValue) {
        for (OpportunityStage stage : values()) {
            if (stage.dbValue.equals(dbValue)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Unknown opportunity stage value: " + dbValue);
    }
}
