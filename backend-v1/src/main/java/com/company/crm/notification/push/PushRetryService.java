package com.company.crm.notification.push;

import com.company.crm.notification.delivery.NotificationDeliveryLog;
import com.company.crm.notification.delivery.NotificationDeliveryLog.Recipient;
import com.company.crm.notification.entity.Notification;
import com.company.crm.notification.entity.UserDevice;
import com.company.crm.notification.repository.NotificationRepository;
import com.company.crm.notification.repository.UserDeviceRepository;
import com.company.crm.notification.settings.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ERR-3: a push that failed for a temporary reason (Firebase busy or unreachable, token refresh) is
 * resent after 1, 5, 15, 60 and 180 minutes, then given up on. Each retry is leased, so two servers
 * never send it twice; the delivery log (NOT-8) gets the final outcome.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushRetryService {

    /** Minutes to wait before attempt n+1 (index = retries so far). */
    static final int[] BACKOFF_MINUTES = {1, 5, 15, 60, 180};
    private static final int LEASE_MINUTES = 10;

    private final JdbcTemplate jdbc;
    private final PushSender pushSender;
    private final NotificationRepository notificationRepository;
    private final UserDeviceRepository deviceRepository;
    private final NotificationDeliveryLog deliveryLog;

    /** Holds a push for a later attempt (once per notification and phone). */
    public void enqueue(Long notificationId, Long deviceId) {
        jdbc.update("INSERT INTO push_retries (notification_id, device_id, next_attempt_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (notification_id, device_id) DO NOTHING",
                notificationId, deviceId, Timestamp.valueOf(LocalDateTime.now().plusMinutes(BACKOFF_MINUTES[0])));
    }

    @Transactional(readOnly = true)
    public List<Long> findDue() {
        return jdbc.queryForList("SELECT id FROM push_retries WHERE status = 'pending' AND next_attempt_at <= ? "
                + "ORDER BY next_attempt_at LIMIT 200", Long.class, Timestamp.valueOf(LocalDateTime.now()));
    }

    @Transactional
    public void retry(Long id) {
        LocalDateTime now = LocalDateTime.now();
        if (jdbc.update("UPDATE push_retries SET next_attempt_at = ? WHERE id = ? AND status = 'pending' AND next_attempt_at <= ?",
                Timestamp.valueOf(now.plusMinutes(LEASE_MINUTES)), id, Timestamp.valueOf(now)) == 0) {
            return; // not due, or another server is on it
        }
        Map<String, Object> row = jdbc.queryForMap("SELECT notification_id, device_id, attempts FROM push_retries WHERE id = ?", id);
        Notification n = notificationRepository.findById(((Number) row.get("notification_id")).longValue()).orElse(null);
        UserDevice device = deviceRepository.findById(((Number) row.get("device_id")).longValue()).orElse(null);
        if (n == null || device == null) {
            jdbc.update("DELETE FROM push_retries WHERE id = ?", id);
            return;
        }
        int attempts = ((Number) row.get("attempts")).intValue() + 1;
        PushSender.Result result = pushSender.send(device.getToken(), n.getTitle(), n.getBody(), PushDispatcher.data(n));
        switch (result) {
            case SENT -> {
                jdbc.update("DELETE FROM push_retries WHERE id = ?", id);
                logOutcome(n, NotificationDeliveryLog.SENT, "Delivered on retry " + attempts);
            }
            case INVALID_TOKEN -> {
                log.info("Removing stale push token for user {}", n.getUserId());
                deviceRepository.delete(device); // its retries go with it
            }
            case RETRY_LATER -> {
                if (attempts >= BACKOFF_MINUTES.length) {
                    giveUp(id, n, attempts, "Firebase kept failing");
                } else {
                    jdbc.update("UPDATE push_retries SET attempts = ?, next_attempt_at = ?, last_error = ? WHERE id = ?", attempts,
                            Timestamp.valueOf(now.plusMinutes(BACKOFF_MINUTES[attempts])), "temporary failure", id);
                }
            }
            case FAILED -> giveUp(id, n, attempts, "Firebase refused the message");
        }
    }

    private void giveUp(Long id, Notification n, int attempts, String why) {
        jdbc.update("UPDATE push_retries SET status = 'dead', attempts = ?, last_error = ? WHERE id = ?", attempts, why, id);
        log.warn("Giving up on push {} for user {} after {} retries: {}", n.getId(), n.getUserId(), attempts, why);
        logOutcome(n, NotificationDeliveryLog.FAILED, why + " — gave up after " + attempts + " retries");
    }

    private void logOutcome(Notification n, String status, String detail) {
        deliveryLog.log(n.getTenantId(), NotificationEvent.fromInAppType(n.getType()), "push", new Recipient("user", n.getUserId(), null),
                n.getId(), null, status, detail);
    }
}
