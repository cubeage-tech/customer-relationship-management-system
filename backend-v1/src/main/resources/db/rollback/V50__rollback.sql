-- Manual rollback for V50__add_ai_privacy_and_data_requests.sql. AI switches, AI exclusions and the
-- data-request log are lost. Data already erased stays erased.

BEGIN;
DROP TABLE IF EXISTS data_requests;
ALTER TABLE leads DROP COLUMN IF EXISTS erased_at;
ALTER TABLE customers DROP COLUMN IF EXISTS erased_at;
ALTER TABLE leads DROP COLUMN IF EXISTS ai_excluded;
ALTER TABLE customers DROP COLUMN IF EXISTS ai_excluded;
ALTER TABLE tenants DROP COLUMN IF EXISTS ai_message_content;
ALTER TABLE tenants DROP COLUMN IF EXISTS ai_enabled;
DELETE FROM flyway_schema_history WHERE version = '50';
COMMIT;
