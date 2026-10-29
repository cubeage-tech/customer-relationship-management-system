package com.company.crm.integration.health;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.exception.ApiException;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * INT-8: watches for an outside service rejecting the CRM's credentials (revoked or expired key,
 * changed password). The first rejection is recorded and the platform admins are told what to
 * re-authorise (again after a day if it is still failing); the next successful call clears it.
 * Never throws: a health record must not break the call that reported it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationHealthService {

    public static final String TWILIO = "twilio";
    public static final String SMTP = "smtp";
    public static final String FCM = "fcm";
    public static final String ANTHROPIC = "anthropic";
    public static final String XAI = "xai";
    public static final String RAZORPAY = "razorpay";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    /** What each integration is and how to re-authorise it (its credentials are deployment settings). */
    public record Integration(String key, String label, String fix) {}

    public static final Map<String, Integration> KNOWN = new LinkedHashMap<>();

    static {
        KNOWN.put(TWILIO, new Integration(TWILIO, "SMS and WhatsApp (Twilio)",
                "Check TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN (a rotated auth token must be updated), then restart the backend."));
        KNOWN.put(SMTP, new Integration(SMTP, "Email (SMTP server)",
                "Check MAIL_USERNAME and MAIL_PASSWORD (or the app password), then restart the backend."));
        KNOWN.put(FCM, new Integration(FCM, "Mobile push (Firebase Cloud Messaging)",
                "Create a new service-account key in Firebase, point FCM_CREDENTIALS_FILE at it, then restart the backend."));
        KNOWN.put(ANTHROPIC, new Integration(ANTHROPIC, "AI assistant (Anthropic)",
                "Create a new API key in the Anthropic Console, set ANTHROPIC_API_KEY, then restart the backend."));
        KNOWN.put(XAI, new Integration(XAI, "AI assistant (xAI Grok)",
                "Create a new API key in the xAI console, set XAI_API_KEY, then restart the backend."));
        KNOWN.put(RAZORPAY, new Integration(RAZORPAY, "Payments (Razorpay)",
                "Check PAYMENT_KEY_ID and PAYMENT_KEY_SECRET in the Razorpay dashboard, then restart the backend."));
    }

    public record HealthRes(String integration, String label, String status, String lastError, LocalDateTime failedAt,
                            LocalDateTime lastOkAt, String fix) {}

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    /** Last status seen per integration, so a healthy call doesn't write to the database every time. */
    private final Map<String, String> lastKnown = new ConcurrentHashMap<>();

    /** The service rejected our credentials. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void authFailed(String integration, String detail) {
        try {
            Map<String, Object> row = jdbc.queryForList("SELECT status, notified_at FROM integration_health WHERE integration = ?", integration)
                    .stream().findFirst().orElse(null);
            boolean wasFailing = row != null && "auth_failed".equals(row.get("status"));
            Timestamp notified = row != null ? (Timestamp) row.get("notified_at") : null;
            boolean notify = !wasFailing || notified == null || notified.toLocalDateTime().isBefore(LocalDateTime.now().minusDays(1));
            jdbc.update("INSERT INTO integration_health (integration, status, last_error, failed_at, notified_at, updated_at)"
                            + " VALUES (?, 'auth_failed', ?, now(), CASE WHEN ? THEN now() END, now())"
                            + " ON CONFLICT (integration) DO UPDATE SET status = 'auth_failed', last_error = EXCLUDED.last_error,"
                            + " failed_at = CASE WHEN integration_health.status = 'auth_failed' THEN integration_health.failed_at ELSE now() END,"
                            + " notified_at = CASE WHEN ? THEN now() ELSE integration_health.notified_at END, updated_at = now()",
                    integration, truncate(detail), notify, notify);
            lastKnown.put(integration, "auth_failed");
            if (notify) {
                log.error("Integration {} rejected the CRM's credentials: {}", integration, detail);
                auditService.recordSystem(null, AuditAction.INTEGRATION_AUTH_FAILED, "integration", null, integration + ": " + truncate(detail));
                eventPublisher.publishEvent(new IntegrationAuthFailedEvent(integration, detail));
            }
        } catch (RuntimeException e) {
            log.warn("Could not record the {} credential failure: {}", integration, e.getMessage());
        }
    }

    /** A call to the service worked: any recorded credential problem is over. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ok(String integration) {
        if ("ok".equals(lastKnown.get(integration))) {
            return;
        }
        try {
            jdbc.update("INSERT INTO integration_health (integration, status, last_ok_at, updated_at) VALUES (?, 'ok', now(), now())"
                    + " ON CONFLICT (integration) DO UPDATE SET status = 'ok', last_ok_at = now(), updated_at = now()", integration);
            lastKnown.put(integration, "ok");
        } catch (RuntimeException e) {
            log.warn("Could not record {} as healthy: {}", integration, e.getMessage());
        }
    }

    /** A short warning for screens that depend on the integration, while its credentials are rejected. */
    @Transactional(readOnly = true)
    public Optional<String> problem(String integration) {
        return jdbc.queryForList("SELECT failed_at FROM integration_health WHERE integration = ? AND status = 'auth_failed'", Timestamp.class, integration)
                .stream().findFirst().map(t -> KNOWN.get(integration).label() + " stopped accepting the CRM's credentials on "
                        + WHEN.format(t.toLocalDateTime()) + "; the platform admin has been told");
    }

    @Transactional(readOnly = true)
    public List<HealthRes> list() {
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        jdbc.queryForList("SELECT * FROM integration_health").forEach(r -> rows.put((String) r.get("integration"), r));
        return KNOWN.values().stream().map(i -> {
            Map<String, Object> r = rows.get(i.key());
            return new HealthRes(i.key(), i.label(), r == null ? "unknown" : (String) r.get("status"), r == null ? null : (String) r.get("last_error"),
                    r == null ? null : time(r.get("failed_at")), r == null ? null : time(r.get("last_ok_at")), i.fix());
        }).toList();
    }

    /** A platform admin re-authorised the integration: clear the warning until the next rejection. */
    @Transactional
    public HealthRes resolve(User superAdmin, String integration) {
        if (!KNOWN.containsKey(integration)) {
            throw ApiException.notFound("Unknown integration " + integration);
        }
        jdbc.update("UPDATE integration_health SET status = 'ok', updated_at = now() WHERE integration = ?", integration);
        lastKnown.remove(integration);
        auditService.record(superAdmin, AuditAction.INTEGRATION_AUTH_FAILED, "integration", null, integration + ": marked re-authorised");
        return list().stream().filter(h -> h.integration().equals(integration)).findFirst().orElseThrow();
    }

    private static LocalDateTime time(Object value) {
        return value == null ? null : ((Timestamp) value).toLocalDateTime();
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 300 ? value : value.substring(0, 299) + "…";
    }
}
