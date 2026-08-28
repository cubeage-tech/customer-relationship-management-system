-- =============================================================================
-- Module 3 — Sales Pipeline Management
-- =============================================================================

CREATE TABLE opportunities (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  customer_id BIGINT NOT NULL REFERENCES customers(id),
  -- Set when this opportunity was auto-created by converting a lead (BR-2).
  lead_id BIGINT REFERENCES leads(id),
  product_service VARCHAR(255),
  deal_value NUMERIC(14, 2) NOT NULL DEFAULT 0,
  expected_closing_date DATE,
  stage VARCHAR(20) NOT NULL DEFAULT 'qualification'
    CHECK (stage IN ('qualification', 'proposal', 'negotiation', 'won', 'lost')),
  -- FR-3.4: required once stage = 'lost'.
  loss_reason VARCHAR(500),
  -- Salesperson this deal is assigned to — drives the "own records" data scope
  -- for the sales_executive role (see permission.constant.js ROLE_DATA_SCOPE).
  owner_id BIGINT REFERENCES users(id),
  stage_changed_at TIMESTAMP,
  stage_changed_by BIGINT REFERENCES users(id),
  created_by BIGINT REFERENCES users(id),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_opportunities_tenant ON opportunities(tenant_id);
CREATE INDEX idx_opportunities_owner ON opportunities(owner_id);
CREATE INDEX idx_opportunities_customer ON opportunities(customer_id);
