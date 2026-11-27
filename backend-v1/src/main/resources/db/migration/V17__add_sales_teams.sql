-- =============================================================================
-- Sales teams (Decision 3): a sales_manager sees records owned by members of the
-- team(s) they manage. Purely additive — every existing user starts with no team
-- (team_id NULL), which keeps today's visibility, so no backfill is required.
-- Behaviour is additionally gated by app.features.sales-team-scope.
-- Rollback: db/rollback/V17__rollback.sql.
-- =============================================================================

CREATE TABLE sales_teams (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  name VARCHAR(100) NOT NULL,
  manager_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  CONSTRAINT uq_sales_teams_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_sales_teams_tenant_manager ON sales_teams(tenant_id, manager_id);

-- A user belongs to at most one team; deleting a team simply un-assigns its members.
ALTER TABLE users ADD COLUMN team_id BIGINT REFERENCES sales_teams(id) ON DELETE SET NULL;

CREATE INDEX idx_users_team ON users(team_id);
