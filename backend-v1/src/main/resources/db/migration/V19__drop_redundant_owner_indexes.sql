-- =============================================================================
-- V18 added (tenant_id, owner_id, stage|status) composites for own/team-scoped
-- summaries. EXPLAIN on realistic data showed PostgreSQL never picks them: a user
-- belongs to exactly one tenant, so the existing single-column owner_id indexes
-- (idx_*_owner, V6–V9) are already as selective, and the composites only add write
-- cost. V18 is not edited because it may already be applied; this drops them.
-- Rollback: db/rollback/V19__rollback.sql.
-- =============================================================================

DROP INDEX IF EXISTS idx_leads_tenant_owner_stage;
DROP INDEX IF EXISTS idx_opportunities_tenant_owner_stage;
DROP INDEX IF EXISTS idx_quotations_tenant_owner_status;
