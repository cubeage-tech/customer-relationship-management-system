-- Manual rollback for V60__add_shared_coaching.sql. Shared coaching plans, notes and replies are lost.

BEGIN;
DROP TABLE IF EXISTS coaching_plans;
DELETE FROM flyway_schema_history WHERE version = '60';
COMMIT;
