-- =============================================================================
-- Indexes for the dashboard summary queries, the service-agent queue and the
-- discount-approval queue. Each leads with tenant_id (every query is tenant-
-- scoped), then the owner / assignee / status / date columns those queries filter
-- on. Verified with EXPLAIN in DashboardIndexUsageTest.
-- Index-only change: no data is touched. Rollback: db/rollback/V18__rollback.sql.
-- =============================================================================

-- Leads: own/team scope (owner) + open/stage counts; "new this month".
CREATE INDEX idx_leads_tenant_owner_stage ON leads(tenant_id, owner_id, stage);
CREATE INDEX idx_leads_tenant_created ON leads(tenant_id, created_at);

-- Opportunities: own/team pipeline; won-in-period; closing this week.
CREATE INDEX idx_opportunities_tenant_owner_stage ON opportunities(tenant_id, owner_id, stage);
CREATE INDEX idx_opportunities_tenant_stage_changed ON opportunities(tenant_id, stage, stage_changed_at);
CREATE INDEX idx_opportunities_tenant_closing ON opportunities(tenant_id, expected_closing_date);

-- Quotations: own/team status counts; approval queue (pending discounts, newest first).
CREATE INDEX idx_quotations_tenant_owner_status ON quotations(tenant_id, owner_id, status);
CREATE INDEX idx_quotations_tenant_discount_created ON quotations(tenant_id, discount_approval_status, created_at DESC);

-- Service tickets: agent's own / unassigned queue by status; SLA windows; resolved-in-period.
CREATE INDEX idx_service_tickets_tenant_tech_status ON service_tickets(tenant_id, assigned_technician_id, status);
CREATE INDEX idx_service_tickets_tenant_status_sla ON service_tickets(tenant_id, status, sla_due_at);
CREATE INDEX idx_service_tickets_tenant_resolved ON service_tickets(tenant_id, resolved_at);

-- Customers: "new this quarter".
CREATE INDEX idx_customers_tenant_created ON customers(tenant_id, created_at);
