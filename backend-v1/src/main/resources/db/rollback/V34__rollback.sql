-- Manual rollback for V34__add_sales_force_management.sql. Targets, visits, attendance and
-- commission history are lost.

BEGIN;
DROP TABLE IF EXISTS commission_entries;
DROP TABLE IF EXISTS commission_rules;
DROP TABLE IF EXISTS attendance_records;
ALTER TABLE quotations DROP COLUMN IF EXISTS sent_at;
DROP TABLE IF EXISTS sales_activities;
DROP TABLE IF EXISTS sales_targets;
DELETE FROM flyway_schema_history WHERE version = '34';
COMMIT;
