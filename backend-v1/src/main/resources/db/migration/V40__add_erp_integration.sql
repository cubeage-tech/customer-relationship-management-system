-- API-G12: generic ERP integration. An ERP authenticates with a tenant API key (only its SHA-256
-- is stored) to pull customers, won deals and approved quotations, and to push back its own
-- customer codes and product prices. erp_code links a CRM record to the ERP's record.

CREATE TABLE integration_api_keys (
    id            BIGSERIAL     PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL REFERENCES tenants(id),
    name          VARCHAR(100)  NOT NULL,
    -- first characters of the key, shown so admins can tell keys apart; never enough to use it
    key_prefix    VARCHAR(12)   NOT NULL,
    key_hash      VARCHAR(64)   NOT NULL UNIQUE,
    created_by    BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    last_used_at  TIMESTAMP,
    revoked_at    TIMESTAMP
);
CREATE INDEX idx_integration_api_keys_tenant ON integration_api_keys(tenant_id);

ALTER TABLE customers ADD COLUMN erp_code VARCHAR(64);
CREATE UNIQUE INDEX uq_customers_erp_code ON customers(tenant_id, erp_code) WHERE erp_code IS NOT NULL;

ALTER TABLE products ADD COLUMN erp_code VARCHAR(64);
CREATE UNIQUE INDEX uq_products_erp_code ON products(tenant_id, erp_code) WHERE erp_code IS NOT NULL;
