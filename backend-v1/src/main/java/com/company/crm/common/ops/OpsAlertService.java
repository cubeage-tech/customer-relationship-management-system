package com.company.crm.common.ops;

import com.company.crm.common.mail.MailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ERR-5: critical errors (unhandled request errors, failed scheduled jobs) are logged with an
 * incident reference and alert the operations team, by email (OPS_ALERT_EMAILS) and/or a chat
 * webhook (OPS_ALERT_WEBHOOK_URL, Slack/Teams/Google Chat "text" payload). The same error is
 * alerted at most once per throttle window, with a count of the repeats. Alerts carry the error
 * type, where it happened and the request path, never the message or data, which stay in the log.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpsAlertService {

    private static final String APP_PACKAGE = "com.company.crm.";

    private final MailService mailService;
    private final ObjectMapper objectMapper;

    @Value("${app.ops.alert-emails:}")
    private String alertEmails;

    @Value("${app.ops.alert-webhook-url:}")
    private String webhookUrl;

    @Value("${app.ops.alert-throttle-minutes:15}")
    private long throttleMinutes;

    @Value("${app.ops.environment:${spring.application.name}}")
    private String environment;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ops-alerts");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static final class Window {
        Instant openedAt;
        final AtomicInteger suppressed = new AtomicInteger();
    }

    /** A short reference to quote to support and to find the full error in the log. */
    public static String newIncidentId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public boolean isConfigured() {
        return !recipients().isEmpty() || !webhookUrl.isBlank();
    }

    /**
     * Raises an alert for {@code error} (already logged by the caller with the same incident id).
     * Returns whether an alert was queued (false when not configured or throttled).
     */
    public boolean critical(String incidentId, String where, Throwable error) {
        if (!isConfigured()) {
            return false;
        }
        String signature = error.getClass().getName() + "@" + origin(error);
        Instant now = Instant.now();
        Window w = windows.computeIfAbsent(signature, k -> new Window());
        int repeats;
        synchronized (w) {
            if (w.openedAt != null && w.openedAt.plus(Duration.ofMinutes(throttleMinutes)).isAfter(now)) {
                w.suppressed.incrementAndGet();
                return false;
            }
            w.openedAt = now;
            repeats = w.suppressed.getAndSet(0);
        }
        String subject = "[" + environment + "] Critical error: " + error.getClass().getSimpleName() + " (" + incidentId + ")";
        String text = "A critical error occurred in " + environment + ".\n\n"
                + "Incident: " + incidentId + "\n"
                + "Where: " + where + "\n"
                + "Error: " + error.getClass().getName() + "\n"
                + "At: " + origin(error) + "\n"
                + "Time: " + now + "\n"
                + (repeats > 0 ? "Repeated " + repeats + " more time(s) since the last alert.\n" : "")
                + "\nSearch the server log for the incident reference for the full details.";
        sender.execute(() -> deliver(subject, text));
        return true;
    }

    private void deliver(String subject, String text) {
        for (String to : recipients()) {
            try {
                mailService.sendCalendarEmail(to, subject, text, null);
            } catch (Exception e) {
                log.warn("Ops alert email to {} failed: {}", to, e.getMessage());
            }
        }
        if (!webhookUrl.isBlank()) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of("text", subject + "\n" + text))))
                        .build();
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 300) {
                    log.warn("Ops alert webhook answered {}", response.statusCode());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("Ops alert webhook failed: {}", e.getMessage());
            }
        }
    }

    /** The first stack frame in our code (else the top frame): groups repeats of the same fault. */
    static String origin(Throwable error) {
        StackTraceElement[] trace = error.getStackTrace();
        return Arrays.stream(trace).filter(f -> f.getClassName().startsWith(APP_PACKAGE)).findFirst()
                .or(() -> Arrays.stream(trace).findFirst())
                .map(f -> f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber())
                .orElse("unknown");
    }

    private List<String> recipients() {
        return Arrays.stream(alertEmails.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @PreDestroy
    void shutdown() {
        sender.shutdown();
    }
}
