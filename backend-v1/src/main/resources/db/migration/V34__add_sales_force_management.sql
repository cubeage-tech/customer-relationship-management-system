-- =============================================================================
-- Module 8 — Sales force management. Additive only. Rollback: db/rollback/V34__rollback.sql.
--   FR-8.1/8.2  sales_targets: revenue or won-deal (unit) targets per salesperson or
--               team for a period; attainment is computed live from won deals (AC-8.1);
--   FR-8.3      sales_activities: manually logged field visits (calls, meetings and
--               quotations sent are counted from their own tables; quotations.sent_at);
--   FR-8.4      attendance_records: check-in/out with optional location, one open per user;
--   FR-8.5      commission_rules + commission_entries: recorded when a deal is won (AC-8.2).
-- =============================================================================

CREATE TABLE sales_targets (
    id           BIGSERIAL      PRIMARY KEY,
    tenant_id    BIGINT         NOT NULL REFERENCES tenants(id),
    user_id      BIGINT         REFERENCES users(id) ON DELETE CASCADE,
    team_id      BIGINT         REFERENCES sales_teams(id) ON DELETE CASCADE,
    metric       VARCHAR(10)    NOT NULL CHECK (metric IN ('revenue', 'units')),
    target_value NUMERIC(14, 2) NOT NULL CHECK (target_value > 0),
    period_start DATE           NOT NULL,
    period_end   DATE           NOT NULL,
    created_by   BIGINT         REFERENCES users(id),
    created_at   TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP      NOT NULL DEFAULT now(),
    CHECK ((user_id IS NULL) <> (team_id IS NULL)),
    CHECK (period_end >= period_start)
);
CREATE INDEX idx_sales_targets_tenant_period ON sales_targets(tenant_id, period_start, period_end);

CREATE TABLE sales_activities (
    id          BIGSERIAL     PRIMARY KEY,
    tenant_id   BIGINT        NOT NULL REFERENCES tenants(id),
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    type        VARCHAR(20)   NOT NULL DEFAULT 'visit' CHECK (type IN ('visit')),
    customer_id BIGINT        REFERENCES customers(id),
    lead_id     BIGINT        REFERENCES leads(id) ON DELETE SET NULL,
    occurred_at TIMESTAMP     NOT NULL,
    notes       TEXT,
    latitude    NUMERIC(9, 6),
    longitude   NUMERIC(9, 6),
    created_at  TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_sales_activities_user ON sales_activities(tenant_id, user_id, occurred_at);

ALTER TABLE quotations ADD COLUMN sent_at TIMESTAMP;

CREATE TABLE attendance_records (
    id             BIGSERIAL     PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL REFERENCES tenants(id),
    user_id        BIGINT        NOT NULL REFERENCES users(id),
    check_in_at    TIMESTAMP     NOT NULL,
    check_out_at   TIMESTAMP,
    check_in_lat   NUMERIC(9, 6),
    check_in_lng   NUMERIC(9, 6),
    check_out_lat  NUMERIC(9, 6),
    check_out_lng  NUMERIC(9, 6),
    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    CHECK (check_out_at IS NULL OR check_out_at >= check_in_at)
);
CREATE INDEX idx_attendance_tenant_day ON attendance_records(tenant_id, check_in_at);
CREATE UNIQUE INDEX uq_attendance_open_per_user ON attendance_records(user_id) WHERE check_out_at IS NULL;

CREATE TABLE commission_rules (
    id             BIGSERIAL     PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL REFERENCES tenants(id),
    name           VARCHAR(100)  NOT NULL,
    priority       INT           NOT NULL DEFAULT 100,
    active         BOOLEAN       NOT NULL DEFAULT true,
    rate_percent   NUMERIC(5, 2) NOT NULL CHECK (rate_percent > 0 AND rate_percent <= 100),
    user_id        BIGINT        REFERENCES users(id) ON DELETE CASCADE,
    team_id        BIGINT        REFERENCES sales_teams(id) ON DELETE CASCADE,
    min_deal_value NUMERIC(14, 2) CHECK (min_deal_value >= 0),
    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT now(),
    CHECK (user_id IS NULL OR team_id IS NULL)
);
CREATE INDEX idx_commission_rules_tenant ON commission_rules(tenant_id, priority);

CREATE TABLE commission_entries (
    id             BIGSERIAL      PRIMARY KEY,
    tenant_id      BIGINT         NOT NULL REFERENCES tenants(id),
    opportunity_id BIGINT         NOT NULL UNIQUE REFERENCES opportunities(id) ON DELETE CASCADE,
    user_id        BIGINT         NOT NULL REFERENCES users(id),
    rule_id        BIGINT         REFERENCES commission_rules(id) ON DELETE SET NULL,
    rule_name      VARCHAR(100)   NOT NULL,
    deal_value     NUMERIC(14, 2) NOT NULL,
    rate_percent   NUMERIC(5, 2)  NOT NULL,
    amount         NUMERIC(14, 2) NOT NULL,
    status         VARCHAR(10)    NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'paid', 'void')),
    won_at         TIMESTAMP      NOT NULL,
    approved_by    BIGINT         REFERENCES users(id),
    approved_at    TIMESTAMP,
    paid_at        TIMESTAMP,
    created_at     TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP      NOT NULL DEFAULT now()
);
CREATE INDEX idx_commission_entries_user ON commission_entries(tenant_id, user_id, won_at);
