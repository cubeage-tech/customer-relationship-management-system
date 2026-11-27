-- Manual rollback for V20__add_quotation_customer_approved_at.sql.
-- Drops the column; purchase history then falls back to quotations.updated_at.

BEGIN;
ALTER TABLE quotations DROP COLUMN IF EXISTS customer_approved_at;
DELETE FROM flyway_schema_history WHERE version = '20';
COMMIT;
