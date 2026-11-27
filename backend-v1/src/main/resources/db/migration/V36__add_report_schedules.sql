-- REP-SCH: reports generated automatically and emailed. Each schedule runs as its owner, so the
-- emailed report has exactly the role permissions and data scope the owner has when it runs (REP-SCOPE).
CREATE TABLE report_schedules (
    id            BIGSERIAL     PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL REFERENCES tenants(id),
    owner_id      BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          VARCHAR(150)  NOT NULL,
    report_type   VARCHAR(30)   NOT NULL CHECK (report_type IN ('sales', 'customers', 'revenue', 'team-performance', 'service', 'campaigns')),
    format        VARCHAR(10)   NOT NULL CHECK (format IN ('pdf', 'xlsx')),
    -- daily = yesterday, weekly = the previous 7 days, monthly = the previous calendar month
    frequency     VARCHAR(10)   NOT NULL CHECK (frequency IN ('daily', 'weekly', 'monthly')),
    hour_of_day   INTEGER       NOT NULL DEFAULT 7 CHECK (hour_of_day BETWEEN 0 AND 23),
    -- comma-separated emails of users in the same tenant
    recipients    VARCHAR(1000) NOT NULL,
    industry      VARCHAR(50),
    team_id       BIGINT        REFERENCES sales_teams(id) ON DELETE SET NULL,
    user_id       BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    next_run_at   TIMESTAMP     NOT NULL,
    last_run_at   TIMESTAMP,
    last_status   VARCHAR(10)   CHECK (last_status IN ('sent', 'failed')),
    last_error    VARCHAR(500),
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_report_schedules_tenant ON report_schedules(tenant_id);
CREATE INDEX idx_report_schedules_due ON report_schedules(next_run_at) WHERE active;
