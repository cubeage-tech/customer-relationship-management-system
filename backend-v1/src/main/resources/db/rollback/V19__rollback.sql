-- Manual rollback for V19__drop_redundant_owner_indexes.sql: re-creates the three V18 composites.
-- Index-only and safe at any time.

BEGIN;
CREATE INDEX IF NOT EXISTS idx_leads_tenant_owner_stage ON leads(tenant_id, owner_id, stage);
CREATE INDEX IF NOT EXISTS idx_opportunities_tenant_owner_stage ON opportunities(tenant_id, owner_id, stage);
CREATE INDEX IF NOT EXISTS idx_quotations_tenant_owner_status ON quotations(tenant_id, owner_id, status);
DELETE FROM flyway_schema_history WHERE version = '19';
COMMIT;
