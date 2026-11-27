-- Manual rollback for V26__add_quotation_view_tracking.sql. Customer view links stop working;
-- statuses already set ('viewed', 'expired') are kept.

BEGIN;
DROP INDEX IF EXISTS idx_quotations_open_valid_until;
DROP INDEX IF EXISTS uq_quotations_view_token;
ALTER TABLE quotations DROP COLUMN IF EXISTS viewed_at;
ALTER TABLE quotations DROP COLUMN IF EXISTS view_token;
DELETE FROM flyway_schema_history WHERE version = '26';
COMMIT;
