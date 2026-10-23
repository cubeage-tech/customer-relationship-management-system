-- =============================================================================
-- Module 6 additions. Additive only. Rollback: db/rollback/V32__rollback.sql.
--   FR-6.1  customer_assets (equipment a ticket can be about), tickets raised by
--           customers through the tenant's public support form (support_form_token);
--   FR-6.3b ticket_assignment_rules: priority/industry -> named agent, or
--           round-robin over the active service agents (assign_user_id NULL);
--   FR-6.4c one at-risk and one breach escalation per ticket;
--   FR-6.5  feedback_token: the customer's rating link, sent once on resolution.
-- =============================================================================

CREATE TABLE customer_assets (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL REFERENCES tenants(id),
    customer_id   BIGINT       NOT NULL REFERENCES customers(id),
    name          VARCHAR(150) NOT NULL,
    model         VARCHAR(100),
    serial_number VARCHAR(100),
    installed_on  DATE,
    notes         TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_customer_assets_customer ON customer_assets(tenant_id, customer_id);

ALTER TABLE service_tickets ADD COLUMN asset_id BIGINT REFERENCES customer_assets(id) ON DELETE SET NULL;
ALTER TABLE service_tickets ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'staff' CHECK (source IN ('staff', 'customer'));
ALTER TABLE service_tickets ADD COLUMN reporter_email VARCHAR(150);
ALTER TABLE service_tickets ADD COLUMN sla_at_risk_notified_at TIMESTAMP;
ALTER TABLE service_tickets ADD COLUMN sla_breach_notified_at TIMESTAMP;
ALTER TABLE service_tickets ADD COLUMN feedback_token VARCHAR(64);
ALTER TABLE service_tickets ADD COLUMN feedback_at TIMESTAMP;
CREATE UNIQUE INDEX uq_service_tickets_feedback_token ON service_tickets(feedback_token) WHERE feedback_token IS NOT NULL;
CREATE INDEX idx_service_tickets_open_sla ON service_tickets(sla_due_at)
    WHERE status IN ('open', 'assigned', 'in_progress');

ALTER TABLE tenants ADD COLUMN support_form_token VARCHAR(64);
CREATE UNIQUE INDEX uq_tenants_support_form_token ON tenants(support_form_token) WHERE support_form_token IS NOT NULL;

CREATE TABLE ticket_assignment_rules (
    id                    BIGSERIAL    PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL REFERENCES tenants(id),
    name                  VARCHAR(100) NOT NULL,
    priority              INT          NOT NULL DEFAULT 100,
    active                BOOLEAN      NOT NULL DEFAULT true,
    match_priority        VARCHAR(20)  CHECK (match_priority IN ('critical', 'high', 'medium', 'low')),
    match_industry        VARCHAR(30),
    assign_user_id        BIGINT       REFERENCES users(id) ON DELETE CASCADE,
    last_assigned_user_id BIGINT,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_assignment_rules_tenant ON ticket_assignment_rules(tenant_id, priority);
