-- =============================================================================
-- Append-only audit trail for security-relevant business actions: discount
-- approvals, ticket claims, team and membership changes. Never updated or
-- deleted by the application. Rollback: db/rollback/V16__rollback.sql.
-- =============================================================================

CREATE TABLE audit_events (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT REFERENCES tenants(id),
  actor_user_id BIGINT REFERENCES users(id),
  action VARCHAR(60) NOT NULL,
  entity_type VARCHAR(60) NOT NULL,
  entity_id BIGINT,
  -- Short human-readable detail; never secrets or personal data beyond ids/names.
  details VARCHAR(1000),
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_events_tenant_created ON audit_events(tenant_id, created_at DESC);
CREATE INDEX idx_audit_events_entity ON audit_events(entity_type, entity_id);
