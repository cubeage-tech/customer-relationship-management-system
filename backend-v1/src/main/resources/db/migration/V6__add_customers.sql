-- =============================================================================
-- Module 1 — Customer Management (Customer 360°)
-- =============================================================================

CREATE TABLE customers (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  company_name VARCHAR(255) NOT NULL,
  industry VARCHAR(30) NOT NULL
    CHECK (industry IN (
      'manufacturing', 'engineering', 'real_estate', 'construction', 'automobile',
      'industrial_equipment', 'software', 'trading', 'services', 'healthcare', 'other'
    )),
  status VARCHAR(20) NOT NULL DEFAULT 'active'
    CHECK (status IN ('active', 'archived')),
  email VARCHAR(255),
  phone VARCHAR(30),
  website VARCHAR(255),
  address VARCHAR(500),
  notes TEXT,
  -- Salesperson this account is assigned to — drives the "own records" data
  -- scope for the sales_executive role (see permission.constant.js ROLE_DATA_SCOPE).
  owner_id BIGINT REFERENCES users(id),
  created_by BIGINT REFERENCES users(id),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_customers_tenant ON customers(tenant_id);
CREATE INDEX idx_customers_owner ON customers(owner_id);

CREATE TABLE customer_contacts (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  designation VARCHAR(255),
  phone VARCHAR(30),
  email VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_contacts_customer ON customer_contacts(customer_id);
