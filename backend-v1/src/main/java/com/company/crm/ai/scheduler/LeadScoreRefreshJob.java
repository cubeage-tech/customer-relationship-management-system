package com.company.crm.ai.scheduler;

import com.company.crm.ai.service.LeadScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FR-AI-1.2/1.3: every 30 minutes (and a minute after start-up), refresh every lead's stored AI score —
 * activity, time passing and other leads converting all move it. One tenant's failure doesn't stop the rest.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LeadScoreRefreshJob {

    private final LeadScoringService scoringService;
    private final JdbcTemplate jdbc;

    @Scheduled(initialDelayString = "${app.ai.lead-score-refresh-initial-ms:60000}",
            fixedDelayString = "${app.ai.lead-score-refresh-ms:1800000}")
    public void refresh() {
        List<Long> tenants = jdbc.queryForList("SELECT DISTINCT tenant_id FROM leads", Long.class);
        int leads = 0;
        for (Long tenantId : tenants) {
            try {
                leads += scoringService.refreshTenant(tenantId);
            } catch (RuntimeException e) {
                log.warn("Lead score refresh failed for tenant {}: {}", tenantId, e.getMessage());
            }
        }
        if (leads > 0) {
            log.info("Lead scores refreshed: {} lead(s) in {} tenant(s)", leads, tenants.size());
        }
    }
}
