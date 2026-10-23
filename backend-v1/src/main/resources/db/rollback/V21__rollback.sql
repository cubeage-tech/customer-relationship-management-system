-- Manual rollback for V21__add_communication_logs.sql. Drops all logged communications.

BEGIN;
DROP TABLE IF EXISTS sms_alert_rules;
DROP TABLE IF EXISTS communication_logs;
DELETE FROM flyway_schema_history WHERE version = '21';
COMMIT;
