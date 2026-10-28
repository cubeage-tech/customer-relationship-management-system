-- FR-AI-5.2: AI-written drafts are reviewed and approved by a person before anything is sent. Each
-- draft is recorded (a hash of its text, not the text), and the message sent from it records who
-- approved it and whether they edited it first.

CREATE TABLE ai_drafts (
    id                BIGSERIAL    PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL REFERENCES tenants(id),
    user_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel           VARCHAR(20)  NOT NULL CHECK (channel IN ('email', 'whatsapp', 'sms')),
    content_hash      VARCHAR(64)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    approved_at       TIMESTAMP,
    edited            BOOLEAN,
    communication_id  BIGINT       REFERENCES communication_logs(id) ON DELETE SET NULL
);
CREATE INDEX idx_ai_drafts_user ON ai_drafts(user_id, created_at DESC);
CREATE INDEX idx_ai_drafts_communication ON ai_drafts(communication_id);
