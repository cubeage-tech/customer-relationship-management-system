package com.company.crm.notification.settings;

import java.util.Arrays;

/** NOT-6: how a notification reaches someone. In-app also pushes to the person's phone (NOT-4). */
public enum NotificationChannel {
    IN_APP("in_app"),
    EMAIL("email"),
    SMS("sms"),
    WHATSAPP("whatsapp");

    private final String code;

    NotificationChannel(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static NotificationChannel fromCode(String code) {
        return Arrays.stream(values()).filter(c -> c.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown channel: " + code));
    }
}
