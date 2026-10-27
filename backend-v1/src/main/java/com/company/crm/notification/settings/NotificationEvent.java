package com.company.crm.notification.settings;

import com.company.crm.common.enums.SmsAlertEvent;
import com.company.crm.notification.entity.Notification;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import static com.company.crm.notification.settings.NotificationChannel.*;

/**
 * NOT-5/NOT-6: the events that notify someone, who they notify, the channels each can use and
 * which are on until a tenant admin changes them. SMS is configured through the FR-5.3 SMS alert
 * rules ({@link #smsEvent}), which also say who receives it.
 */
public enum NotificationEvent {

    // ---- to the customer ----
    QUOTATION_SENT("quotation_sent", "Quotation sent", Audience.CUSTOMER,
            Set.of(EMAIL, SMS, WHATSAPP), Set.of(EMAIL), SmsAlertEvent.QUOTATION_SENT, null),
    TICKET_STATUS_CHANGED("ticket_status_changed", "Service request progress", Audience.CUSTOMER,
            Set.of(EMAIL, SMS, WHATSAPP), Set.of(EMAIL), SmsAlertEvent.TICKET_STATUS_CHANGED, null),

    // ---- to CRM users ----
    FOLLOW_UP_DUE("follow_up_due", "Lead follow-up due", Audience.STAFF,
            Set.of(IN_APP, EMAIL, SMS), Set.of(IN_APP, EMAIL), SmsAlertEvent.FOLLOW_UP_DUE, Notification.FOLLOW_UP),
    RECORD_ASSIGNED("record_assigned", "Lead or deal assigned to someone", Audience.STAFF,
            Set.of(IN_APP, EMAIL), Set.of(IN_APP), null, Notification.ASSIGNMENT),
    TICKET_SLA_ESCALATED("ticket_sla_escalated", "Service ticket SLA at risk or breached", Audience.STAFF,
            Set.of(IN_APP, EMAIL, SMS), Set.of(IN_APP, EMAIL), SmsAlertEvent.TICKET_ESCALATED, Notification.SLA_ESCALATION),
    DISCOUNT_APPROVAL_REQUESTED("discount_approval_requested", "Quotation discount needs approval", Audience.STAFF,
            Set.of(IN_APP, EMAIL), Set.of(IN_APP), null, Notification.DISCOUNT_APPROVAL),
    AI_CHURN_RISK("ai_churn_risk", "AI alert: a customer message reads as unhappy", Audience.STAFF,
            Set.of(IN_APP, EMAIL), Set.of(), null, Notification.AI_ALERT),
    QUOTATION_RESPONSE("quotation_response", "Customer accepted or declined a quotation", Audience.STAFF,
            Set.of(IN_APP, EMAIL), Set.of(IN_APP, EMAIL), null, Notification.QUOTATION_RESPONSE),
    MANAGER_MESSAGE("manager_message", "Message from a manager", Audience.STAFF,
            Set.of(IN_APP), Set.of(IN_APP), null, Notification.MESSAGE),

    // ---- to platform admins (not configurable per tenant) ----
    INTEGRATION_AUTH_FAILED("integration_auth_failed", "An integration's credentials were rejected", Audience.PLATFORM,
            Set.of(IN_APP, EMAIL), Set.of(IN_APP, EMAIL), null, Notification.INTEGRATION);

    public enum Audience { CUSTOMER, STAFF, PLATFORM }

    private final String code;
    private final String label;
    private final Audience audience;
    private final Set<NotificationChannel> channels;
    private final Set<NotificationChannel> defaults;
    private final SmsAlertEvent smsEvent;
    private final String inAppType;

    NotificationEvent(String code, String label, Audience audience, Set<NotificationChannel> channels,
                      Set<NotificationChannel> defaults, SmsAlertEvent smsEvent, String inAppType) {
        this.code = code;
        this.label = label;
        this.audience = audience;
        this.channels = channels;
        this.defaults = defaults;
        this.smsEvent = smsEvent;
        this.inAppType = inAppType;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
    public Audience getAudience() { return audience; }
    public Set<NotificationChannel> getChannels() { return channels; }
    public Set<NotificationChannel> getDefaults() { return defaults; }
    public SmsAlertEvent getSmsEvent() { return smsEvent; }
    public String getInAppType() { return inAppType; }

    /** Whether a tenant admin can change its channels (manager messages and platform events can't be switched off). */
    public boolean isConfigurable() {
        return audience != Audience.PLATFORM && this != MANAGER_MESSAGE;
    }

    /** The event behind an in-app notification type (for the delivery log). */
    public static NotificationEvent fromInAppType(String type) {
        return Arrays.stream(values()).filter(e -> type != null && type.equals(e.inAppType)).findFirst().orElse(MANAGER_MESSAGE);
    }

    public static Optional<NotificationEvent> fromCode(String code) {
        return Arrays.stream(values()).filter(e -> e.code.equals(code)).findFirst();
    }
}
