package com.company.crm.notification.push;

import java.util.Map;

/** FR-M.4: delivers one push message to one device. */
public interface PushSender {

    enum Result { SENT, INVALID_TOKEN, FAILED }

    /** False when no push provider is configured — notifications then stay in the in-app inbox only. */
    boolean isEnabled();

    Result send(String token, String title, String body, Map<String, String> data);
}
