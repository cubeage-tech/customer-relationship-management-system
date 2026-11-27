-- Manual rollback for V16__add_audit_events.sql (Flyway Community has no undo).
-- Not on the Flyway path, never runs automatically. Roll back V17 first if it is applied.
-- WARNING: drops the audit trail. Export it first if it must be kept:
--   \copy audit_events TO 'audit_events_backup.csv' CSV HEADER

BEGIN;
DROP TABLE IF EXISTS audit_events;
DELETE FROM flyway_schema_history WHERE version = '16';
COMMIT;
