package com.company.crm.common.enums;

/** Events that can trigger an SMS alert — see SRS FR-5.3. */
public enum SmsAlertEvent {
    QUOTATION_SENT("quotation_sent"),
    TICKET_STATUS_CHANGED("ticket_status_changed");

    private final String dbValue;

    SmsAlertEvent(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static SmsAlertEvent fromDbValue(String dbValue) {
        for (SmsAlertEvent value : values()) {
            if (value.dbValue.equals(dbValue)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown SMS alert event value: " + dbValue);
    }
}
