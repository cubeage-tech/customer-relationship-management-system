-- =============================================================================
-- FR-2.3: a lead can be assigned to a sales team (in addition to, or before, a
-- specific owner). The team's manager sees the lead in their queue. Deleting a
-- team simply unassigns its leads. Additive and nullable.
-- Rollback: db/rollback/V22__rollback.sql.
-- =============================================================================

ALTER TABLE leads ADD COLUMN team_id BIGINT REFERENCES sales_teams(id) ON DELETE SET NULL;

CREATE INDEX idx_leads_tenant_team ON leads(tenant_id, team_id);
