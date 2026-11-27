-- Manual rollback for V25__add_lead_assignment_rules.sql. Rules are dropped; leads keep their owner/team.

BEGIN;
DROP TABLE IF EXISTS lead_assignment_rules;
DELETE FROM flyway_schema_history WHERE version = '25';
COMMIT;
