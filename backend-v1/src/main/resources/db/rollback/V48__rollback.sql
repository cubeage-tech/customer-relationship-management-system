-- Manual rollback for V48__add_refresh_token_rotation.sql. Every refresh token stops working; users
-- sign in again when their access token expires.

BEGIN;
DELETE FROM refresh_tokens;
DROP INDEX IF EXISTS idx_refresh_tokens_family;
DROP INDEX IF EXISTS idx_refresh_tokens_user;
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS revoked_reason;
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS family;
DELETE FROM flyway_schema_history WHERE version = '48';
COMMIT;
