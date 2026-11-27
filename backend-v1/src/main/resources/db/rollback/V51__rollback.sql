-- Manual rollback for V51__add_editions.sql. The tenants' edition choice is lost; the custom fields
-- and pipeline stages it added stay.

BEGIN;
ALTER TABLE tenants DROP COLUMN IF EXISTS edition;
DELETE FROM flyway_schema_history WHERE version = '51';
COMMIT;
