-- =============================================================================
-- FR-9.1: each user configures their dashboard's headline metrics — which tiles
-- show (comma-separated keys) and the default period. Additive only.
-- Rollback: db/rollback/V35__rollback.sql.
-- =============================================================================

CREATE TABLE dashboard_preferences (
    user_id    BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    widgets    VARCHAR(500) NOT NULL,
    period     VARCHAR(20)  NOT NULL DEFAULT 'month' CHECK (period IN ('month', 'quarter', 'year', 'last30')),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);
