package com.company.crm.notification.event;

/** FR-M.4: a notification was stored; it is pushed to the user's devices once that commits. */
public record NotificationCreatedEvent(Long notificationId) {}
