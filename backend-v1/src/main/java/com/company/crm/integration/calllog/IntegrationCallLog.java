package com.company.crm.integration.calllog;

import com.company.crm.ai.privacy.AiRedactor;
import com.company.crm.common.web.RequestIds;
import com.company.crm.user.entity.User;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * INT-9: a log of every call the CRM makes to an outside service — which provider, what for, the
 * outcome, HTTP status and duration. Payloads are never kept: no message text, recipients, tokens
 * or keys; a provider's error is stored with addresses and numbers removed. Calls are buffered and
 * written in batches, so logging never slows down or breaks the call itself.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationCallLog {

    public static final String SUCCESS = "success";
    public static final String FAILED = "failed";

    private static final int MAX_PENDING = 10_000;
    private static final int MAX_ERROR = 300;

    private record Entry(Long tenantId, String provider, String operation, String outcome, Integer httpStatus,
                         int durationMs, String error, String requestId, LocalDateTime at) {}

    private final JdbcTemplate jdbc;
    private final Queue<Entry> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();

    @Value("${app.integration.call-log.retention-days:90}")
    private int retentionDays;

    /** One call in progress; finish it with {@link Call#ok} or {@link Call#failed}. */
    public static final class Call {

        private final IntegrationCallLog log;
        private final String provider;
        private final String operation;
        private final long startedAt = System.nanoTime();
        private final Long tenantId;
        private final String requestId;

        private Call(IntegrationCallLog log, String provider, String operation) {
            this.log = log;
            this.provider = provider;
            this.operation = operation;
            this.tenantId = log != null ? currentTenantId() : null;
            this.requestId = log != null ? RequestIds.current() : null;
        }

        public void ok(Integer httpStatus) {
            finish(SUCCESS, httpStatus, null);
        }

        public void failed(Integer httpStatus, String error) {
            finish(FAILED, httpStatus, error);
        }

        private void finish(String outcome, Integer httpStatus, String error) {
            if (log != null) {
                int ms = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedAt) / 1_000_000);
                log.add(new Entry(tenantId, provider, operation, outcome, httpStatus, ms, clean(error), requestId, LocalDateTime.now()));
            }
        }
    }

    /** Starts timing a call. {@code log} may be null (a client built without Spring): nothing is then recorded. */
    public static Call start(IntegrationCallLog log, String provider, String operation) {
        return new Call(log, provider, operation);
    }

    private void add(Entry entry) {
        if (size.incrementAndGet() > MAX_PENDING) {
            size.decrementAndGet();
            log.warn("Integration call log buffer full — dropped {} {}", entry.provider(), entry.operation());
            return;
        }
        pending.add(entry);
    }

    /** Writes the buffered calls (every few seconds, on shutdown, and from tests). */
    @Scheduled(fixedDelayString = "${app.integration.call-log.flush-ms:5000}")
    @PreDestroy
    public synchronized void flush() {
        List<Object[]> rows = new ArrayList<>();
        Entry e;
        while ((e = pending.poll()) != null) {
            size.decrementAndGet();
            rows.add(new Object[]{e.tenantId(), e.provider(), e.operation(), e.outcome(), e.httpStatus(), e.durationMs(),
                    e.error(), e.requestId(), Timestamp.valueOf(e.at())});
        }
        if (rows.isEmpty()) {
            return;
        }
        try {
            jdbc.batchUpdate("INSERT INTO integration_calls (tenant_id, provider, operation, outcome, http_status, duration_ms, error, "
                    + "request_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
        } catch (RuntimeException ex) {
            log.warn("Could not write {} integration call log row(s): {}", rows.size(), ex.getMessage());
        }
    }

    /** Keeps the log to its retention period (90 days by default). */
    @Scheduled(cron = "${app.integration.call-log.purge-cron:0 40 3 * * *}")
    public void purge() {
        int removed = jdbc.update("DELETE FROM integration_calls WHERE created_at < ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(retentionDays)));
        if (removed > 0) {
            log.info("Integration call log: removed {} row(s) older than {} days", removed, retentionDays);
        }
    }

    private static String clean(String error) {
        if (error == null || error.isBlank()) {
            return null;
        }
        String redacted = AiRedactor.redact(error.replaceAll("\\s+", " ").trim());
        return redacted.length() > MAX_ERROR ? redacted.substring(0, MAX_ERROR) : redacted;
    }

    private static Long currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof User user && user.getTenant() != null ? user.getTenant().getId() : null;
    }
}
