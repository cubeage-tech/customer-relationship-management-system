-- Manual rollback for V54__add_ai_drafts.sql. The record of which messages were AI-drafted and
-- approved is lost; the messages themselves stay.

BEGIN;
DROP TABLE IF EXISTS ai_drafts;
DELETE FROM flyway_schema_history WHERE version = '54';
COMMIT;
