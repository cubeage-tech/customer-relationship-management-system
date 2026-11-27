-- SEC-1b: the user's previous password hashes, so a new password can't reuse a recent one.
-- INT-9: every outbound integration call (provider, operation, outcome, timing) — never the payload.
-- ERR-3: push messages that failed for a temporary reason, retried with backoff.

CREATE TABLE password_history (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_history_user ON password_history(user_id, created_at DESC);

CREATE TABLE integration_calls (
    id           BIGSERIAL    PRIMARY KEY,
    -- the tenant whose request made the call; null for platform jobs
    tenant_id    BIGINT       REFERENCES tenants(id),
    provider     VARCHAR(30)  NOT NULL,
    operation    VARCHAR(60)  NOT NULL,
    outcome      VARCHAR(10)  NOT NULL CHECK (outcome IN ('success', 'failed')),
    http_status  INT,
    duration_ms  INT          NOT NULL,
    -- the provider's error, with addresses and numbers removed
    error        VARCHAR(300),
    request_id   VARCHAR(64),
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_integration_calls_created ON integration_calls(created_at DESC);
CREATE INDEX idx_integration_calls_provider ON integration_calls(provider, created_at DESC);

CREATE TABLE push_retries (
    id               BIGSERIAL    PRIMARY KEY,
    notification_id  BIGINT       NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    device_id        BIGINT       NOT NULL REFERENCES user_devices(id) ON DELETE CASCADE,
    attempts         INT          NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMP    NOT NULL,
    status           VARCHAR(10)  NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'dead')),
    last_error       VARCHAR(300),
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (notification_id, device_id)
);
CREATE INDEX idx_push_retries_due ON push_retries(next_attempt_at) WHERE status = 'pending';
