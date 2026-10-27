-- Manual rollback for V36__add_report_schedules.sql. Scheduled report emails stop.

BEGIN;
DROP TABLE IF EXISTS report_schedules;
DELETE FROM flyway_schema_history WHERE version = '36';
COMMIT;
