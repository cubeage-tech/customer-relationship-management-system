-- =============================================================================
-- Module 2 — Lead Management
-- Pipeline (client-defined): new_lead -> contacted -> meeting -> quotation ->
-- negotiation -> converted (see LeadStage — declaration order is pipeline order).
-- =============================================================================

CREATE TABLE leads (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  lead_name VARCHAR(255) NOT NULL,
  company_name VARCHAR(255) NOT NULL,
  contact_email VARCHAR(255),
  contact_phone VARCHAR(30),
  industry VARCHAR(30) NOT NULL
    CHECK (industry IN (
      'manufacturing', 'engineering', 'real_estate', 'construction', 'automobile',
      'industrial_equipment', 'software', 'trading', 'services', 'healthcare', 'other'
    )),
  source VARCHAR(30) NOT NULL
    CHECK (source IN ('website', 'referral', 'exhibition', 'cold_call', 'marketing_campaign', 'other')),
  stage VARCHAR(20) NOT NULL DEFAULT 'new_lead'
    CHECK (stage IN ('new_lead', 'contacted', 'meeting', 'quotation', 'negotiation', 'converted')),
  follow_up_date TIMESTAMP,
  notes TEXT,
  -- Salesperson this lead is assigned to — drives the "own records" data scope
  -- for the sales_executive role (see permission.constant.js ROLE_DATA_SCOPE).
  owner_id BIGINT REFERENCES users(id),
  -- Set once, on conversion (BR-2: a converted lead auto-generates a linked Customer).
  converted_customer_id BIGINT REFERENCES customers(id),
  created_by BIGINT REFERENCES users(id),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_leads_tenant ON leads(tenant_id);
CREATE INDEX idx_leads_owner ON leads(owner_id);
