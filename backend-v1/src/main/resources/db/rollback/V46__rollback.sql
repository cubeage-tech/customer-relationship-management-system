-- Manual rollback for V46__add_mfa.sql. Every two-step sign-in enrolment and recovery code is lost;
-- users sign in with their password only.

BEGIN;
DROP TABLE IF EXISTS mfa_challenges;
DROP TABLE IF EXISTS mfa_recovery_codes;
UPDATE users SET mfa_enabled = false;
ALTER TABLE users DROP COLUMN IF EXISTS mfa_enabled_at;
ALTER TABLE users DROP COLUMN IF EXISTS mfa_last_step;
ALTER TABLE users DROP COLUMN IF EXISTS mfa_pending_secret;
ALTER TABLE users DROP COLUMN IF EXISTS mfa_secret;
DELETE FROM flyway_schema_history WHERE version = '46';
COMMIT;
