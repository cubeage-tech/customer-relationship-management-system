-- FR-AI-8.3: coaching plans (FR-AI-8.2) shared between a salesperson and their manager. Kept as
-- shared — the plan as JSON, the sharer's note — and acknowledged by the salesperson with a reply.
CREATE TABLE coaching_plans (
    id               BIGSERIAL     PRIMARY KEY,
    tenant_id        BIGINT        NOT NULL REFERENCES tenants(id),
    salesperson_id   BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shared_by        BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    period_from      DATE          NOT NULL,
    period_to        DATE          NOT NULL,
    plan             TEXT          NOT NULL,
    source           VARCHAR(10)   NOT NULL CHECK (source IN ('ai', 'rules')),
    note             VARCHAR(1000),
    acknowledged_at  TIMESTAMP,
    reply            VARCHAR(1000),
    created_at       TIMESTAMP     NOT NULL DEFAULT now(),
    CHECK (period_to >= period_from)
);

CREATE INDEX idx_coaching_plans_person ON coaching_plans (tenant_id, salesperson_id, created_at DESC);
