-- =============================================================================
-- FR-1.6: customers are classified from a configurable, per-tenant industry list.
-- Every existing tenant starts with the SRS list (same codes as before, so existing
-- customers stay valid); tenants created later get it on first use. Codes never
-- change — admins rename labels and deactivate entries instead of deleting them.
-- The fixed CHECK on customers.industry is replaced by service-side validation
-- against this table. (leads.industry keeps its fixed list — FR-2.1, unchanged.)
-- Rollback: db/rollback/V24__rollback.sql.
-- =============================================================================

CREATE TABLE industries (
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  code VARCHAR(30) NOT NULL,
  label VARCHAR(60) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, code)
);

CREATE UNIQUE INDEX uq_industries_tenant_label ON industries(tenant_id, lower(label));

INSERT INTO industries (tenant_id, code, label, sort_order)
SELECT t.id, v.code, v.label, v.sort_order
FROM tenants t
CROSS JOIN (VALUES
  ('manufacturing', 'Manufacturing', 1),
  ('engineering', 'Engineering', 2),
  ('real_estate', 'Real Estate', 3),
  ('construction', 'Construction', 4),
  ('automobile', 'Automobile', 5),
  ('industrial_equipment', 'Industrial Equipment', 6),
  ('software', 'Software', 7),
  ('trading', 'Trading', 8),
  ('services', 'Services', 9),
  ('healthcare', 'Healthcare', 10),
  ('other', 'Other', 11)
) AS v(code, label, sort_order);

ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_industry_check;
