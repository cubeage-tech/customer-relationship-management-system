package com.company.crm.common.enums;

/** Outcome of a logged call — see SRS FR-5.4. */
public enum CallOutcome {
    CONNECTED("connected"),
    NO_ANSWER("no_answer"),
    BUSY("busy"),
    VOICEMAIL("voicemail"),
    WRONG_NUMBER("wrong_number");

    private final String dbValue;

    CallOutcome(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CallOutcome fromDbValue(String dbValue) {
        for (CallOutcome value : values()) {
            if (value.dbValue.equals(dbValue)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown call outcome value: " + dbValue);
    }
}
