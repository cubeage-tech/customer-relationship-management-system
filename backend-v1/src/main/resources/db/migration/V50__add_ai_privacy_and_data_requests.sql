-- SEC-5/SEC-6: what may go to the AI provider. A tenant can switch AI off, or keep message text
-- (emails, WhatsApp, SMS) away from it; a customer or lead can be excluded from AI altogether.
-- SEC-8: data-protection requests — export or erase a customer's / lead's personal data, tracked
-- with a due date; erased records keep their business figures but no personal data.

ALTER TABLE tenants ADD COLUMN ai_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenants ADD COLUMN ai_message_content BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE customers ADD COLUMN ai_excluded BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE leads ADD COLUMN ai_excluded BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE customers ADD COLUMN erased_at TIMESTAMP;
ALTER TABLE leads ADD COLUMN erased_at TIMESTAMP;

CREATE TABLE data_requests (
    id            BIGSERIAL      PRIMARY KEY,
    tenant_id     BIGINT         NOT NULL REFERENCES tenants(id),
    subject_type  VARCHAR(20)    NOT NULL CHECK (subject_type IN ('customer', 'lead')),
    subject_id    BIGINT         NOT NULL,
    subject_name  VARCHAR(255),
    request_type  VARCHAR(20)    NOT NULL CHECK (request_type IN ('export', 'erasure')),
    status        VARCHAR(20)    NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'completed', 'rejected')),
    notes         VARCHAR(1000),
    requested_by  BIGINT         REFERENCES users(id) ON DELETE SET NULL,
    requested_at  TIMESTAMP      NOT NULL DEFAULT now(),
    due_at        TIMESTAMP      NOT NULL,
    completed_by  BIGINT         REFERENCES users(id) ON DELETE SET NULL,
    completed_at  TIMESTAMP,
    outcome       VARCHAR(1000)
);
CREATE INDEX idx_data_requests_tenant ON data_requests(tenant_id, requested_at DESC);
