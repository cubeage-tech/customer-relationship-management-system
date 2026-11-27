package com.company.crm.dashboard;

import com.company.crm.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the V18 indexes are what PostgreSQL actually uses for the summary, queue and approval
 * queries. Seeds a realistic multi-tenant volume (60 tenants × 300 rows per table) so per-tenant
 * predicates are selective, ANALYZEs, then EXPLAINs the same predicates the app's queries use.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardIndexUsageTest extends PostgresIntegrationTest {

    private static final String PREFIX = "ExplainCo ";

    @Autowired private JdbcTemplate jdbc;

    private long tenantId;
    private long ownerId;

    /**
     * 20 small tenants (300 rows per table) plus one large tenant (20,000 rows per table), and the
     * queries run against the large one. At a few hundred rows a tenant the plain tenant_id index
     * is the right plan; the composite indexes exist for large tenants, so that is what we prove.
     */
    @BeforeAll
    void seed() {
        ownerId = jdbc.queryForObject("SELECT min(id) FROM users", Long.class);
        jdbc.update("INSERT INTO tenants (company_name) SELECT ? || g FROM generate_series(1, 20) g", PREFIX);
        jdbc.update("INSERT INTO tenants (company_name) VALUES (?)", PREFIX + "big");
        tenantId = jdbc.queryForObject("SELECT id FROM tenants WHERE company_name = ?", Long.class, PREFIX + "big");

        seedRows("SELECT id FROM tenants WHERE company_name LIKE '" + PREFIX + "%' AND id <> " + tenantId, 300);
        seedRows("SELECT id FROM tenants WHERE id = " + tenantId, 20_000);
        // Realistic queues: every other tenant also has a backlog of unassigned tickets, so
        // "technician IS NULL" is not selective platform-wide — only (tenant, technician) is.
        jdbc.update("UPDATE service_tickets SET assigned_technician_id = NULL, status = 'open', resolved_at = NULL "
                + "WHERE tenant_id <> ? AND tenant_id IN (SELECT id FROM tenants WHERE company_name LIKE ?)", tenantId, PREFIX + "%");
        jdbc.execute("ANALYZE service_tickets");

        for (String table : List.of("customers", "leads", "opportunities", "quotations", "service_tickets")) {
            jdbc.execute("ANALYZE " + table);
        }
    }

    /** {@code perTenant} rows in each table for every tenant returned by {@code tenantsSql}. */
    private void seedRows(String tenantsSql, int perTenant) {
        String from = " FROM (" + tenantsSql + ") t CROSS JOIN generate_series(1, " + perTenant + ") g";
        String firstCustomer = "(SELECT min(c.id) FROM customers c WHERE c.tenant_id = t.id)";

        jdbc.update("INSERT INTO customers (tenant_id, company_name, industry, created_at) "
                + "SELECT t.id, 'Customer', 'trading', now() - (g % 400) * interval '1 day'" + from);
        // ~1% of records owned by one user (a sales executive's share), stages/statuses evenly spread.
        jdbc.update("INSERT INTO leads (tenant_id, lead_name, company_name, industry, source, stage, owner_id, created_at) "
                + "SELECT t.id, 'Lead', 'Co', 'trading', 'referral', "
                + "(ARRAY['new_lead','contacted','meeting','converted'])[1 + g % 4], CASE WHEN g % 100 = 0 THEN ? END, "
                + "now() - (g % 365) * interval '1 day'" + from, ownerId);
        jdbc.update("INSERT INTO opportunities (tenant_id, customer_id, stage, deal_value, owner_id, expected_closing_date, stage_changed_at) "
                + "SELECT t.id, " + firstCustomer + ", "
                + "(ARRAY['qualification','proposal','negotiation','won','lost'])[1 + g % 5], g * 10, CASE WHEN g % 100 = 0 THEN ? END, "
                + "current_date + (g % 365), now() - (g % 365) * interval '1 day'" + from, ownerId);
        jdbc.update("INSERT INTO quotations (tenant_id, customer_id, quotation_number, status, discount_approval_status, owner_id, created_at) "
                + "SELECT t.id, " + firstCustomer + ", 'Q-' || t.id || '-' || g, "
                + "(ARRAY['draft','pending','viewed','approved'])[1 + g % 4], "
                + "CASE WHEN g % 50 = 0 THEN 'pending' WHEN g % 50 = 1 THEN 'approved' ELSE 'not_required' END, "
                + "CASE WHEN g % 100 = 0 THEN ? END, now() - (g % 365) * interval '1 day'" + from, ownerId);
        jdbc.update("INSERT INTO service_tickets (tenant_id, customer_id, subject, priority, status, sla_due_at, assigned_technician_id, resolved_at, created_at) "
                + "SELECT t.id, " + firstCustomer + ", 'Ticket', 'medium', "
                + "CASE WHEN g % 20 < 2 THEN (ARRAY['open','assigned'])[1 + g % 2] ELSE (ARRAY['resolved','closed'])[1 + g % 2] END, "
                + "now() + (g % 72) * interval '1 hour', CASE WHEN g % 20 <> 0 THEN ? END, "
                + "CASE WHEN g % 20 >= 2 THEN now() - (g % 365) * interval '1 day' END, "
                + "now() - (g % 365) * interval '1 day'" + from, ownerId);
    }

    @Test
    void agentQueueUsesTenantTechnicianStatusIndex() {
        assertPlanUses("SELECT * FROM service_tickets WHERE tenant_id = " + tenantId
                + " AND assigned_technician_id IS NULL AND status IN ('open','assigned','in_progress')", "idx_service_tickets_tenant_tech_status");
    }

    @Test
    void approvalQueueUsesDiscountStatusIndexWithoutSorting() {
        String plan = assertPlanUses("SELECT * FROM quotations WHERE tenant_id = " + tenantId
                + " AND discount_approval_status = 'pending' ORDER BY created_at DESC LIMIT 20", "idx_quotations_tenant_discount_created");
        assertThat(plan).doesNotContain("Sort");
    }

    @Test
    void wonInPeriodUsesStageChangedIndex() {
        assertPlanUses("SELECT count(*), sum(deal_value) FROM opportunities WHERE tenant_id = " + tenantId
                + " AND stage = 'won' AND stage_changed_at >= now() - interval '30 days'", "idx_opportunities_tenant_stage_changed");
    }

    /** Own/team scope is served by the pre-existing owner_id indexes (owner implies tenant) — see V19. */
    @Test
    void ownScopedSummariesUseOwnerIndexes() {
        assertPlanUses("SELECT count(*) FROM leads WHERE tenant_id = " + tenantId + " AND owner_id IN (" + ownerId + ")",
                "idx_leads_owner");
        assertPlanUses("SELECT count(*) FROM quotations WHERE tenant_id = " + tenantId + " AND owner_id IN (" + ownerId + ")",
                "idx_quotations_owner");
        assertPlanUses("SELECT count(*) FROM opportunities WHERE tenant_id = " + tenantId + " AND owner_id IN (" + ownerId + ")",
                "idx_opportunities_owner");
    }

    @Test
    void periodCountsUseTenantDateIndexes() {
        assertPlanUses("SELECT count(*) FROM service_tickets WHERE tenant_id = " + tenantId
                + " AND resolved_at >= now() - interval '7 days'", "idx_service_tickets_tenant_resolved");
        assertPlanUses("SELECT count(*) FROM customers WHERE tenant_id = " + tenantId
                + " AND created_at >= now() - interval '90 days'", "idx_customers_tenant_created");
    }

    /** Runs EXPLAIN and asserts the plan scans {@code indexName}; returns the plan text. */
    private String assertPlanUses(String sql, String indexName) {
        String plan = String.join("\n", jdbc.queryForList("EXPLAIN " + sql, String.class));
        assertThat(plan).as("plan for: %s%n%s", sql, plan).contains(indexName);
        return plan;
    }
}
