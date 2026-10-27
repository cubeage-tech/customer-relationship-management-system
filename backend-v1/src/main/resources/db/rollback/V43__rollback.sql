-- Manual rollback for V43__add_weekly_digest.sql. Digest opt-outs are lost.

BEGIN;
ALTER TABLE users DROP COLUMN IF EXISTS last_digest_at;
ALTER TABLE users DROP COLUMN IF EXISTS weekly_digest;
DELETE FROM flyway_schema_history WHERE version = '43';
COMMIT;
