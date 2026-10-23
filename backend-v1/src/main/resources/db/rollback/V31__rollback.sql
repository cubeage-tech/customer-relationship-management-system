-- Manual rollback for V31__add_meetings.sql. All meetings and calendar feed links are lost.

BEGIN;
DROP INDEX IF EXISTS uq_users_calendar_feed_token;
ALTER TABLE users DROP COLUMN IF EXISTS calendar_feed_token;
DROP TABLE IF EXISTS meetings;
DELETE FROM flyway_schema_history WHERE version = '31';
COMMIT;
