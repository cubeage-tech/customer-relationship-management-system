-- Manual rollback for V18__add_dashboard_and_queue_indexes.sql (Flyway Community has no undo).
-- Index-only: safe to run at any time; queries keep working, just slower on large tenants.

BEGIN;
DROP INDEX IF EXISTS idx_leads_tenant_owner_stage;
DROP INDEX IF EXISTS idx_leads_tenant_created;
DROP INDEX IF EXISTS idx_opportunities_tenant_owner_stage;
DROP INDEX IF EXISTS idx_opportunities_tenant_stage_changed;
DROP INDEX IF EXISTS idx_opportunities_tenant_closing;
DROP INDEX IF EXISTS idx_quotations_tenant_owner_status;
DROP INDEX IF EXISTS idx_quotations_tenant_discount_created;
DROP INDEX IF EXISTS idx_service_tickets_tenant_tech_status;
DROP INDEX IF EXISTS idx_service_tickets_tenant_status_sla;
DROP INDEX IF EXISTS idx_service_tickets_tenant_resolved;
DROP INDEX IF EXISTS idx_customers_tenant_created;
DELETE FROM flyway_schema_history WHERE version = '18';
COMMIT;
