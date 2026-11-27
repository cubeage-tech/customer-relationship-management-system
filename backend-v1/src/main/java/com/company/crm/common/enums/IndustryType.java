package com.company.crm.common.enums;

/** Industry classification for a customer account — see SRS FR-1.6. */
public enum IndustryType {
    MANUFACTURING("manufacturing"),
    ENGINEERING("engineering"),
    REAL_ESTATE("real_estate"),
    CONSTRUCTION("construction"),
    AUTOMOBILE("automobile"),
    INDUSTRIAL_EQUIPMENT("industrial_equipment"),
    SOFTWARE("software"),
    TRADING("trading"),
    SERVICES("services"),
    HEALTHCARE("healthcare"),
    OTHER("other");

    private final String dbValue;

    IndustryType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static IndustryType fromDbValue(String dbValue) {
        for (IndustryType type : values()) {
            if (type.dbValue.equals(dbValue)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown industry value: " + dbValue);
    }
}
