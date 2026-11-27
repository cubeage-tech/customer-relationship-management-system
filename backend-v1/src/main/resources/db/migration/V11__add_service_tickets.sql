-- =============================================================================
-- Module 6 — Service & Support Management
-- =============================================================================

CREATE TABLE service_tickets (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  customer_id BIGINT NOT NULL REFERENCES customers(id),
  subject VARCHAR(255) NOT NULL,
  description TEXT,
  priority VARCHAR(20) NOT NULL
    CHECK (priority IN ('low', 'medium', 'high', 'critical')),
  status VARCHAR(20) NOT NULL DEFAULT 'open'
    CHECK (status IN ('open', 'assigned', 'in_progress', 'resolved', 'closed')),
  -- FR-6.4: computed at creation from the priority's configured SLA window.
  sla_due_at TIMESTAMP NOT NULL,
  resolved_at TIMESTAMP,
  -- FR-6.5: recorded on the customer's behalf by internal staff — no self-service portal yet.
  feedback_score SMALLINT CHECK (feedback_score BETWEEN 1 AND 5),
  feedback_comment VARCHAR(1000),
  -- Technician this ticket is assigned to — drives the "assigned" data scope
  -- for the service_agent role (see permission.constant.js ROLE_DATA_SCOPE).
  assigned_technician_id BIGINT REFERENCES users(id),
  created_by BIGINT REFERENCES users(id),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_service_tickets_tenant ON service_tickets(tenant_id);
CREATE INDEX idx_service_tickets_technician ON service_tickets(assigned_technician_id);
CREATE INDEX idx_service_tickets_customer ON service_tickets(customer_id);
