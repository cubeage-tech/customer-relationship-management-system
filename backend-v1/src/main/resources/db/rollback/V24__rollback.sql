-- Manual rollback for V24__add_tenant_industries.sql. Customers using a custom industry are
-- moved to 'other' so the original CHECK constraint can be restored.

BEGIN;
UPDATE customers SET industry = 'other'
WHERE industry NOT IN ('manufacturing', 'engineering', 'real_estate', 'construction', 'automobile',
                       'industrial_equipment', 'software', 'trading', 'services', 'healthcare', 'other');
ALTER TABLE customers ADD CONSTRAINT customers_industry_check CHECK (industry IN (
  'manufacturing', 'engineering', 'real_estate', 'construction', 'automobile',
  'industrial_equipment', 'software', 'trading', 'services', 'healthcare', 'other'));
DROP TABLE IF EXISTS industries;
DELETE FROM flyway_schema_history WHERE version = '24';
COMMIT;
