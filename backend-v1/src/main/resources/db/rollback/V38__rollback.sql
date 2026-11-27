-- Manual rollback for V38__add_security_and_reliability.sql.
-- WARNING: run this only after decrypting the encrypted columns (or restoring a backup taken before
-- V38): once the schema is rolled back, the application no longer decrypts customer, contact, lead,
-- user and message contact details. Hashed link tokens can't be restored: outstanding verification/reset links stop working.

BEGIN;
DROP TABLE IF EXISTS tenant_settings;
DROP TABLE IF EXISTS mail_outbox;
DROP INDEX IF EXISTS idx_communication_logs_retry;
ALTER TABLE communication_logs DROP COLUMN IF EXISTS next_retry_at;
ALTER TABLE communication_logs DROP COLUMN IF EXISTS retry_count;
DROP INDEX IF EXISTS idx_communication_logs_counterpart_hash;
ALTER TABLE communication_logs DROP COLUMN IF EXISTS counterpart_hash;
ALTER TABLE leads DROP COLUMN IF EXISTS phone_hash;
ALTER TABLE leads DROP COLUMN IF EXISTS email_hash;
ALTER TABLE customer_contacts DROP COLUMN IF EXISTS phone_hash;
ALTER TABLE customer_contacts DROP COLUMN IF EXISTS email_hash;
ALTER TABLE customers DROP COLUMN IF EXISTS phone_hash;
ALTER TABLE customers DROP COLUMN IF EXISTS email_hash;
DELETE FROM flyway_schema_history WHERE version = '38';
COMMIT;
