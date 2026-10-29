package com.company.crm.ai.scheduler;

import com.company.crm.ai.service.ChurnRiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FR-AI-6.2: every six hours (and two minutes after start-up), reassess every active customer's churn
 * risk — time passing alone (no purchase, gone quiet) raises it. One tenant's failure doesn't stop the rest.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChurnRiskRefreshJob {

    private final ChurnRiskService churnRisk;
    private final JdbcTemplate jdbc;

    @Scheduled(initialDelayString = "${app.ai.churn-refresh-initial-ms:120000}",
            fixedDelayString = "${app.ai.churn-refresh-ms:21600000}")
    public void refresh() {
        List<Long> tenants = jdbc.queryForList("SELECT DISTINCT tenant_id FROM customers WHERE status = 'active'", Long.class);
        int customers = 0;
        for (Long tenantId : tenants) {
            try {
                customers += churnRisk.refreshTenant(tenantId);
            } catch (RuntimeException e) {
                log.warn("Churn risk refresh failed for tenant {}: {}", tenantId, e.getMessage());
            }
        }
        if (customers > 0) {
            log.info("Churn risk reassessed: {} customer(s) in {} tenant(s)", customers, tenants.size());
        }
    }
}
