-- Module 5 — Customer communication history across supported channels.
CREATE TABLE communication_logs (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  created_by BIGINT NOT NULL REFERENCES users(id),
  channel VARCHAR(20) NOT NULL
    CHECK (channel IN ('email', 'whatsapp', 'sms', 'call')),
  direction VARCHAR(20) NOT NULL
    CHECK (direction IN ('inbound', 'outbound')),
  subject VARCHAR(255),
  content TEXT,
  contact_name VARCHAR(150),
  contact_address VARCHAR(255),
  status VARCHAR(20) NOT NULL DEFAULT 'logged'
    CHECK (status IN ('logged', 'sent', 'delivered', 'received', 'failed', 'missed')),
  occurred_at TIMESTAMP NOT NULL DEFAULT now(),
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_communication_logs_customer_timeline
  ON communication_logs(tenant_id, customer_id, occurred_at DESC, id DESC);
