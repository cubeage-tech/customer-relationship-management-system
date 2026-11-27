-- Non-functional requirements, SRS §7.
--   NFR-7.2b  personal contact data encrypted at rest (AES-256-GCM in the application, key from
--             DATA_ENCRYPTION_KEY). Encrypted values are longer, so the columns become TEXT; the
--             *_hash columns hold keyed HMACs so exact-match lookups (inbound email/SMS) still work.
--             Existing rows are encrypted by the application on startup (PiiEncryptionBackfill).
--             Verification and reset tokens are stored as SHA-256 hashes, not as the link value.
--   NFR-7.4a  failed outbound email is kept and retried (mail_outbox); failed SMS/WhatsApp are
--             retried from their communication log row.
--   NFR-7.6b  per-tenant SLA hours and discount-approval threshold (tenant_settings).

ALTER TABLE customers ALTER COLUMN email TYPE TEXT;
ALTER TABLE customers ALTER COLUMN phone TYPE TEXT;
ALTER TABLE customers ALTER COLUMN address TYPE TEXT;
ALTER TABLE customers ADD COLUMN email_hash VARCHAR(64);
ALTER TABLE customers ADD COLUMN phone_hash VARCHAR(64);
CREATE INDEX idx_customers_email_hash ON customers(email_hash);
CREATE INDEX idx_customers_phone_hash ON customers(phone_hash);

ALTER TABLE customer_contacts ALTER COLUMN email TYPE TEXT;
ALTER TABLE customer_contacts ALTER COLUMN phone TYPE TEXT;
ALTER TABLE customer_contacts ADD COLUMN email_hash VARCHAR(64);
ALTER TABLE customer_contacts ADD COLUMN phone_hash VARCHAR(64);
CREATE INDEX idx_customer_contacts_email_hash ON customer_contacts(email_hash);
CREATE INDEX idx_customer_contacts_phone_hash ON customer_contacts(phone_hash);

ALTER TABLE leads ALTER COLUMN contact_email TYPE TEXT;
ALTER TABLE leads ALTER COLUMN contact_phone TYPE TEXT;
ALTER TABLE leads ADD COLUMN email_hash VARCHAR(64);
ALTER TABLE leads ADD COLUMN phone_hash VARCHAR(64);
CREATE INDEX idx_leads_email_hash ON leads(email_hash);
CREATE INDEX idx_leads_phone_hash ON leads(phone_hash);

ALTER TABLE users ALTER COLUMN phone TYPE TEXT;

-- Payment detail captured at signup.
ALTER TABLE tenants ALTER COLUMN bank_account_number TYPE TEXT;

ALTER TABLE campaign_recipients ALTER COLUMN address TYPE TEXT;

ALTER TABLE communication_logs ALTER COLUMN counterpart TYPE TEXT;
ALTER TABLE communication_logs ADD COLUMN counterpart_hash VARCHAR(64);
CREATE INDEX idx_communication_logs_counterpart_hash ON communication_logs(channel, direction, counterpart_hash, occurred_at DESC);

-- Link tokens: only their SHA-256 is kept. Links already emailed keep working (the app hashes the
-- token it receives before looking it up).
ALTER TABLE email_verification_tokens ALTER COLUMN token TYPE VARCHAR(64);
ALTER TABLE password_reset_tokens ALTER COLUMN token TYPE VARCHAR(64);
UPDATE email_verification_tokens SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex');
UPDATE password_reset_tokens SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex');

-- NFR-7.4a: SMS/WhatsApp retries.
ALTER TABLE communication_logs ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE communication_logs ADD COLUMN next_retry_at TIMESTAMP;
CREATE INDEX idx_communication_logs_retry ON communication_logs(next_retry_at) WHERE next_retry_at IS NOT NULL;

-- NFR-7.4a: email that could not be handed to the mail server, kept whole (MIME) for retry.
CREATE TABLE mail_outbox (
    id               BIGSERIAL    PRIMARY KEY,
    message_id       VARCHAR(255),
    recipients       TEXT         NOT NULL,
    subject          VARCHAR(500),
    mime             BYTEA        NOT NULL,
    status           VARCHAR(10)  NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'sent', 'dead')),
    attempts         INT          NOT NULL DEFAULT 1,
    next_attempt_at  TIMESTAMP    NOT NULL,
    last_error       VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    sent_at          TIMESTAMP
);
CREATE INDEX idx_mail_outbox_due ON mail_outbox(next_attempt_at) WHERE status = 'pending';

-- NFR-7.6b: per-tenant thresholds; NULL = the deployment default (application.properties).
CREATE TABLE tenant_settings (
    tenant_id                    BIGINT       PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    sla_hours_critical           INT          CHECK (sla_hours_critical BETWEEN 1 AND 2160),
    sla_hours_high               INT          CHECK (sla_hours_high BETWEEN 1 AND 2160),
    sla_hours_medium             INT          CHECK (sla_hours_medium BETWEEN 1 AND 2160),
    sla_hours_low                INT          CHECK (sla_hours_low BETWEEN 1 AND 2160),
    discount_approval_threshold  NUMERIC(5,2) CHECK (discount_approval_threshold BETWEEN 0 AND 100),
    updated_at                   TIMESTAMP    NOT NULL DEFAULT now()
);
