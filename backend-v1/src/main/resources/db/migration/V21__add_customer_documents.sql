-- Customer 360 documents are private files; this table stores metadata and opaque storage keys.
CREATE TABLE customer_documents (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  uploaded_by BIGINT NOT NULL REFERENCES users(id),
  original_filename VARCHAR(255) NOT NULL,
  storage_key VARCHAR(80) NOT NULL UNIQUE,
  content_type VARCHAR(120) NOT NULL,
  file_size BIGINT NOT NULL CHECK (file_size > 0),
  uploaded_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_documents_timeline
  ON customer_documents(tenant_id, customer_id, uploaded_at DESC, id DESC);
