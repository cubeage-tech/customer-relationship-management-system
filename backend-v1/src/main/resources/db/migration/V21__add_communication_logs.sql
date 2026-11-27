-- =============================================================================
-- Module 5 — Communication Management.
--   DB-E09  communication_logs: one row per email, WhatsApp message, SMS or call,
--           logged against a customer and/or a lead (FR-5.1–5.4) and read back as
--           the customer's single timeline (FR-5.6).
--   FR-5.3  sms_alert_rules: which events send an SMS alert, per tenant.
-- Additive only. Rollback: db/rollback/V21__rollback.sql.
-- =============================================================================

CREATE TABLE communication_logs (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  customer_id BIGINT REFERENCES customers(id),
  lead_id BIGINT REFERENCES leads(id),
  channel VARCHAR(20) NOT NULL
    CHECK (channel IN ('email', 'whatsapp', 'sms', 'call')),
  direction VARCHAR(10) NOT NULL
    CHECK (direction IN ('inbound', 'outbound')),
  status VARCHAR(20) NOT NULL
    CHECK (status IN ('logged', 'queued', 'sent', 'delivered', 'read', 'failed', 'received')),
  subject VARCHAR(255),
  body TEXT,
  -- Email address or phone number on the customer's side.
  counterpart VARCHAR(255),
  -- Email Message-ID or provider message SID; lets replies and status callbacks find their row.
  provider_message_id VARCHAR(255),
  error_message VARCHAR(500),
  call_outcome VARCHAR(20)
    CHECK (call_outcome IN ('connected', 'no_answer', 'busy', 'voicemail', 'wrong_number')),
  call_duration_seconds INTEGER CHECK (call_duration_seconds >= 0),
  occurred_at TIMESTAMP NOT NULL,
  created_by BIGINT REFERENCES users(id),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  CHECK (customer_id IS NOT NULL OR lead_id IS NOT NULL)
);

CREATE INDEX idx_communication_logs_customer ON communication_logs(tenant_id, customer_id, occurred_at DESC);
CREATE INDEX idx_communication_logs_lead ON communication_logs(tenant_id, lead_id, occurred_at DESC);
-- Webhook retries and re-polled mailboxes must not create duplicates.
CREATE UNIQUE INDEX uq_communication_logs_provider_message ON communication_logs(provider_message_id)
  WHERE provider_message_id IS NOT NULL;

CREATE TABLE sms_alert_rules (
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  event VARCHAR(40) NOT NULL
    CHECK (event IN ('quotation_sent', 'ticket_status_changed')),
  enabled BOOLEAN NOT NULL DEFAULT false,
  -- customer = the customer's phone; internal = the record's owner / assigned technician.
  recipient VARCHAR(20) NOT NULL DEFAULT 'customer'
    CHECK (recipient IN ('customer', 'internal')),
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, event)
);
