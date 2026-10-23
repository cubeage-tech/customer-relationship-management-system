-- =============================================================================
-- FR-2.3: configurable auto-assignment rules for new leads. A rule matches on
-- lead source and/or industry (NULL = any) and assigns either a named user or a
-- sales team; for a team the owner rotates round-robin among its sales
-- executives (last_assigned_user_id is the cursor). Lowest priority wins.
-- Deleting the target user or team deletes the rule. Additive only.
-- Rollback: db/rollback/V25__rollback.sql.
-- =============================================================================

CREATE TABLE lead_assignment_rules (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL REFERENCES tenants(id),
    name                  VARCHAR(100) NOT NULL,
    priority              INT          NOT NULL DEFAULT 100,
    active                BOOLEAN      NOT NULL DEFAULT true,
    match_source          VARCHAR(30),
    match_industry        VARCHAR(30),
    assign_user_id        BIGINT       REFERENCES users(id) ON DELETE CASCADE,
    assign_team_id        BIGINT       REFERENCES sales_teams(id) ON DELETE CASCADE,
    last_assigned_user_id BIGINT,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT lead_assignment_rules_one_target
        CHECK ((assign_user_id IS NULL) <> (assign_team_id IS NULL))
);

CREATE INDEX idx_lead_assignment_rules_tenant ON lead_assignment_rules(tenant_id, priority);
