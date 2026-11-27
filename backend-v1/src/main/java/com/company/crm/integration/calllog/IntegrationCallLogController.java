package com.company.crm.integration.calllog;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** INT-9: the platform admin reads the outbound integration call log — the last day per provider, and each call. */
@RestController
@RequestMapping("/api/integration-calls")
@RequiredArgsConstructor
public class IntegrationCallLogController {

    private static final int MAX_SIZE = 200;

    private final IntegrationCallLog callLog;
    private final JdbcTemplate jdbc;

    public record CallRes(Long id, LocalDateTime at, Long tenantId, String provider, String operation, String outcome,
                          Integer httpStatus, int durationMs, String error, String requestId) {}

    public record ProviderSummary(String provider, long calls, long failures, long averageMs, LocalDateTime lastFailureAt) {}

    public record CallPage(List<ProviderSummary> lastDay, List<CallRes> calls, long total, int page, int size) {}

    @GetMapping
    @PreAuthorize(RoleExpressions.SUPER_ADMIN)
    @Operation(summary = "INT-9: outbound integration calls (newest first) with a per-provider summary of the last 24 hours")
    public Response<CallPage> list(@RequestParam(required = false) String provider,
                                   @RequestParam(required = false) String outcome,
                                   @RequestParam(required = false) Long tenantId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        callLog.flush(); // include calls made in the last few seconds
        int pageSize = Math.max(1, Math.min(size, MAX_SIZE));
        int pageNo = Math.max(0, page);

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (provider != null && !provider.isBlank()) {
            where.append(" AND provider = ?");
            args.add(provider.trim());
        }
        if (outcome != null && !outcome.isBlank()) {
            where.append(" AND outcome = ?");
            args.add(outcome.trim());
        }
        if (tenantId != null) {
            where.append(" AND tenant_id = ?");
            args.add(tenantId);
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM integration_calls" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((long) pageNo * pageSize);
        List<CallRes> calls = jdbc.query("SELECT id, created_at, tenant_id, provider, operation, outcome, http_status, duration_ms, error, "
                        + "request_id FROM integration_calls" + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new CallRes(rs.getLong("id"), rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getObject("tenant_id", Long.class), rs.getString("provider"), rs.getString("operation"),
                        rs.getString("outcome"), rs.getObject("http_status", Integer.class), rs.getInt("duration_ms"),
                        rs.getString("error"), rs.getString("request_id")),
                pageArgs.toArray());

        List<ProviderSummary> lastDay = jdbc.query("SELECT provider, count(*) AS calls, count(*) FILTER (WHERE outcome = 'failed') AS failures, "
                        + "coalesce(round(avg(duration_ms)), 0) AS avg_ms, max(created_at) FILTER (WHERE outcome = 'failed') AS last_failure "
                        + "FROM integration_calls WHERE created_at > ? GROUP BY provider ORDER BY provider",
                (rs, i) -> new ProviderSummary(rs.getString("provider"), rs.getLong("calls"), rs.getLong("failures"), rs.getLong("avg_ms"),
                        rs.getTimestamp("last_failure") != null ? rs.getTimestamp("last_failure").toLocalDateTime() : null),
                Timestamp.valueOf(LocalDateTime.now().minusHours(24)));
        return Response.ok(new CallPage(lastDay, calls, total, pageNo, pageSize));
    }
}
