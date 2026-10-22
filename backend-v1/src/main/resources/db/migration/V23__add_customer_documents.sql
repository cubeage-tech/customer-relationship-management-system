-- =============================================================================
-- FR-1.5: documents (contracts, purchase orders…) attached to a customer.
-- Only metadata lives here; the bytes live in document storage under storage_key
-- (local disk today, cloud storage later — ARC-7). Additive only.
-- Rollback: db/rollback/V23__rollback.sql (stored files must be removed separately).
-- =============================================================================

CREATE TABLE customer_documents (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  customer_id BIGINT NOT NULL REFERENCES customers(id),
  -- Generated path inside document storage (tenant/customer/uuid) — never derived from the file name.
  storage_key VARCHAR(255) NOT NULL UNIQUE,
  file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
  uploaded_by BIGINT REFERENCES users(id),
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_documents_customer ON customer_documents(tenant_id, customer_id, created_at DESC);
