-- Manual rollback for V40__add_erp_integration.sql. ERP API keys and ERP codes are lost; ERPs
-- using the integration stop working.

BEGIN;
DROP INDEX IF EXISTS uq_products_erp_code;
ALTER TABLE products DROP COLUMN IF EXISTS erp_code;
DROP INDEX IF EXISTS uq_customers_erp_code;
ALTER TABLE customers DROP COLUMN IF EXISTS erp_code;
DROP TABLE IF EXISTS integration_api_keys;
DELETE FROM flyway_schema_history WHERE version = '40';
COMMIT;
