-- =============================================================================
-- Module 4 — Quotation & Proposal Management
-- =============================================================================

-- FR-4.2: product/service price list used to populate quotation line items.
CREATE TABLE products (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  name VARCHAR(255) NOT NULL,
  description VARCHAR(500),
  unit_price NUMERIC(14, 2) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_tenant ON products(tenant_id);

CREATE TABLE quotations (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  customer_id BIGINT NOT NULL REFERENCES customers(id),
  opportunity_id BIGINT REFERENCES opportunities(id),
  quotation_number VARCHAR(30) NOT NULL,
  -- FR-4.5: the customer's response to the quotation.
  status VARCHAR(20) NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft', 'pending', 'viewed', 'approved', 'rejected', 'expired')),
  -- BR-3/FR-4.3: set automatically from the line items' discount %, checked before sending.
  discount_approval_status VARCHAR(20) NOT NULL DEFAULT 'not_required'
    CHECK (discount_approval_status IN ('not_required', 'pending', 'approved', 'rejected')),
  valid_until DATE,
  notes TEXT,
  -- Salesperson this quotation is assigned to — drives the "own records" data scope
  -- for the sales_executive role (see permission.constant.js ROLE_DATA_SCOPE).
  owner_id BIGINT REFERENCES users(id),
  created_by BIGINT REFERENCES users(id),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, quotation_number)
);

CREATE INDEX idx_quotations_tenant ON quotations(tenant_id);
CREATE INDEX idx_quotations_owner ON quotations(owner_id);
CREATE INDEX idx_quotations_customer ON quotations(customer_id);

-- FR-4.1: line items — quantity, unit price, and a per-line discount.
CREATE TABLE quotation_line_items (
  id BIGSERIAL PRIMARY KEY,
  quotation_id BIGINT NOT NULL REFERENCES quotations(id) ON DELETE CASCADE,
  product_name VARCHAR(255) NOT NULL,
  quantity NUMERIC(12, 2) NOT NULL CHECK (quantity > 0),
  unit_price NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
  discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0
    CHECK (discount_percent >= 0 AND discount_percent <= 100)
);

CREATE INDEX idx_quotation_line_items_quotation ON quotation_line_items(quotation_id);
