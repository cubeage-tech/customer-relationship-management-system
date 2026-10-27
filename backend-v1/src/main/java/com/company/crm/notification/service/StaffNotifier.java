package com.company.crm.notification.service;

import com.company.crm.common.mail.MailQueuedException;
import com.company.crm.common.mail.MailService;
import com.company.crm.notification.delivery.NotificationDeliveryLog;
import com.company.crm.notification.delivery.NotificationDeliveryLog.Recipient;
import com.company.crm.notification.event.RecordAssignedEvent;
import com.company.crm.notification.settings.NotificationChannel;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.notification.settings.NotificationSettingsService;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NOT-5/NOT-6: tells CRM users about an event through the channels the tenant chose for it —
 * in-app (also pushed to their phone, NOT-4) and email — logging each delivery (NOT-8). SMS to
 * users goes through the FR-5.3 SMS alerts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffNotifier {

    private final NotificationService notificationService;
    private final NotificationSettingsService settings;
    private final NotificationDeliveryLog deliveryLog;
    private final MailService mailService;
    private final UserRepository userRepository;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * Notifies each recipient once. {@code path} (e.g. "/quotations/12") is linked from the email.
     * {@code tenantId} is null for platform notifications to super admins.
     */
    @Transactional
    public void notify(Long tenantId, NotificationEvent event, Collection<User> recipients, String title, String body,
                       String entityType, Long entityId, Long senderId, String path) {
        boolean inApp = settings.isEnabled(tenantId, event, NotificationChannel.IN_APP);
        boolean email = settings.isEnabled(tenantId, event, NotificationChannel.EMAIL);
        Map<Long, User> unique = new LinkedHashMap<>();
        recipients.stream().filter(u -> u != null && u.getId() != null).forEach(u -> unique.putIfAbsent(u.getId(), u));
        for (User user : unique.values()) {
            if (inApp) {
                notificationService.notify(tenantId, user.getId(), event.getInAppType(), title, body, entityType, entityId, senderId);
            }
            if (email) {
                email(tenantId, event, user, title, (body == null ? "" : body) + (path != null ? "\n\nOpen it: " + frontendUrl + path : ""));
            }
        }
    }

    /** One plain email to a user, logged as sent, queued (mail server down, retried) or failed. */
    public void email(Long tenantId, NotificationEvent event, User user, String subject, String body) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            deliveryLog.skipped(tenantId, event, "email", Recipient.user(user), "No email address");
            return;
        }
        try {
            mailService.sendCalendarEmail(user.getEmail(), subject, body, null);
            deliveryLog.log(tenantId, event, "email", Recipient.user(user), null, null, NotificationDeliveryLog.SENT, null);
        } catch (MailQueuedException e) {
            deliveryLog.log(tenantId, event, "email", Recipient.user(user), null, null, NotificationDeliveryLog.QUEUED,
                    "Mail server unavailable — retrying automatically");
        } catch (Exception e) {
            log.warn("{} email to user {} failed: {}", event.getCode(), user.getId(), e.getMessage());
            deliveryLog.log(tenantId, event, "email", Recipient.user(user), null, null, NotificationDeliveryLog.FAILED, e.getMessage());
        }
    }

    /** A lead or opportunity was assigned to someone else: tell them, once the assignment commits. */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRecordAssigned(RecordAssignedEvent e) {
        userRepository.findById(e.assigneeId()).ifPresent(assignee -> notify(e.tenantId(), NotificationEvent.RECORD_ASSIGNED, List.of(assignee),
                e.title(), e.detail() + (e.assignedByName() != null ? " · assigned by " + e.assignedByName() : ""),
                e.entityType(), e.entityId(), e.assignedById(), ("opportunity".equals(e.entityType()) ? "/opportunities/" : "/leads/") + e.entityId()));
    }
}
