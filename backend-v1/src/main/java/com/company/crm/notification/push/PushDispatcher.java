package com.company.crm.notification.push;

import com.company.crm.notification.delivery.NotificationDeliveryLog;
import com.company.crm.notification.delivery.NotificationDeliveryLog.Recipient;
import com.company.crm.notification.entity.Notification;
import com.company.crm.notification.entity.UserDevice;
import com.company.crm.notification.event.NotificationCreatedEvent;
import com.company.crm.notification.repository.NotificationRepository;
import com.company.crm.notification.repository.UserDeviceRepository;
import com.company.crm.notification.settings.NotificationEvent;
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
    private final NotificationDeliveryLog deliveryLog;
    private final PushRetryService retryService;

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
        Map<String, String> data = data(n);
        int devices = 0;
        int sent = 0;
        int queued = 0;
        for (UserDevice device : deviceRepository.findByUserId(n.getUserId())) {
            devices++;
            PushSender.Result result = pushSender.send(device.getToken(), n.getTitle(), n.getBody(), data);
            if (result == PushSender.Result.SENT) {
                sent++;
            } else if (result == PushSender.Result.INVALID_TOKEN) {
                log.info("Removing stale push token for user {}", n.getUserId());
                deviceRepository.delete(device);
            } else if (result == PushSender.Result.RETRY_LATER) {
                retryService.enqueue(n.getId(), device.getId()); // ERR-3
                queued++;
            }
        }
        // NOT-8: the push, for users with the app on a phone.
        if (devices > 0) {
            String status = sent > 0 ? NotificationDeliveryLog.SENT : queued > 0 ? NotificationDeliveryLog.QUEUED : NotificationDeliveryLog.FAILED;
            String detail = sent > 0 ? sent + " of " + devices + " phone(s)" : "Push failed on " + devices + " phone(s)";
            if (queued > 0) {
                detail += "; retrying on " + queued;
            }
            deliveryLog.log(n.getTenantId(), NotificationEvent.fromInAppType(n.getType()), "push", new Recipient("user", n.getUserId(), null),
                    n.getId(), null, status, detail);
        }
    }

    /** What the app needs to open the record from the push. */
    static Map<String, String> data(Notification n) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", String.valueOf(n.getId()));
        data.put("type", n.getType());
        if (n.getEntityType() != null) {
            data.put("entityType", n.getEntityType());
            data.put("entityId", String.valueOf(n.getEntityId()));
        }
        return data;
    }
}
