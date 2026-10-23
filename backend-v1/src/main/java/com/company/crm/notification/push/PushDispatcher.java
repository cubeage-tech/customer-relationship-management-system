package com.company.crm.notification.push;

import com.company.crm.notification.entity.Notification;
import com.company.crm.notification.entity.UserDevice;
import com.company.crm.notification.event.NotificationCreatedEvent;
import com.company.crm.notification.repository.NotificationRepository;
import com.company.crm.notification.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * FR-M.4: once a notification is committed, push it to each of the user's devices. Tokens FCM
 * reports as gone (app uninstalled, token rotated) are removed. A push failure never affects the
 * notification itself — it is still in the inbox the app syncs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PushDispatcher {

    private final PushSender pushSender;
    private final NotificationRepository notificationRepository;
    private final UserDeviceRepository deviceRepository;

    /** Its own transaction: after the commit, the original one can no longer write (stale-token removal). */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        if (!pushSender.isEnabled()) {
            return;
        }
        Notification n = notificationRepository.findById(event.notificationId()).orElse(null);
        if (n == null) {
            return;
        }
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", String.valueOf(n.getId()));
        data.put("type", n.getType());
        if (n.getEntityType() != null) {
            data.put("entityType", n.getEntityType());
            data.put("entityId", String.valueOf(n.getEntityId()));
        }
        for (UserDevice device : deviceRepository.findByUserId(n.getUserId())) {
            PushSender.Result result = pushSender.send(device.getToken(), n.getTitle(), n.getBody(), data);
            if (result == PushSender.Result.INVALID_TOKEN) {
                log.info("Removing stale push token for user {}", n.getUserId());
                deviceRepository.delete(device);
            }
        }
    }
}
