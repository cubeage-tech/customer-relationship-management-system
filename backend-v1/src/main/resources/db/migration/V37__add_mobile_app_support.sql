-- Mobile app (FR-M.1 – FR-M.6).
--   FR-M.1  field visits record a purpose and an outcome.
--   FR-M.3  meeting reports: outcome, notes and next steps after a meeting.
--   FR-M.4  notifications (assignments, follow-ups, manager messages) and the devices they are pushed to.
--   FR-M.6  client_ref: the app's id for a visit recorded offline, so a retried sync can't create it twice.

ALTER TABLE sales_activities ADD COLUMN purpose VARCHAR(200);
ALTER TABLE sales_activities ADD COLUMN outcome VARCHAR(500);
ALTER TABLE sales_activities ADD COLUMN client_ref VARCHAR(64);
CREATE UNIQUE INDEX uq_sales_activities_client_ref ON sales_activities(user_id, client_ref) WHERE client_ref IS NOT NULL;

ALTER TABLE meetings ADD COLUMN report_outcome VARCHAR(20)
    CHECK (report_outcome IN ('positive', 'neutral', 'negative', 'no_show'));
ALTER TABLE meetings ADD COLUMN report_notes TEXT;
ALTER TABLE meetings ADD COLUMN next_steps TEXT;
ALTER TABLE meetings ADD COLUMN reported_at TIMESTAMP;
ALTER TABLE meetings ADD COLUMN reported_by BIGINT REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE notifications (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL REFERENCES tenants(id),
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         VARCHAR(20)  NOT NULL CHECK (type IN ('assignment', 'follow_up', 'message')),
    title        VARCHAR(200) NOT NULL,
    body         VARCHAR(2000),
    -- the record it is about, for opening it from the notification
    entity_type  VARCHAR(20)  CHECK (entity_type IN ('lead', 'opportunity')),
    entity_id    BIGINT,
    sender_id    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    read_at      TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications(user_id) WHERE read_at IS NULL;

CREATE TABLE user_devices (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token         VARCHAR(512) NOT NULL UNIQUE,
    platform      VARCHAR(10)  NOT NULL CHECK (platform IN ('android', 'ios')),
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_devices_user ON user_devices(user_id);
