-- Manual rollback for V59__add_follow_up_tasks.sql. All follow-up tasks are lost.

BEGIN;
DROP TABLE IF EXISTS follow_up_tasks;
DELETE FROM flyway_schema_history WHERE version = '59';
COMMIT;
