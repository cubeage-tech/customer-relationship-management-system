-- Manual rollback for V61__add_duplicate_dismissals.sql. "Not a duplicate" decisions are lost, so
-- those pairs are suggested again.

BEGIN;
DROP TABLE IF EXISTS duplicate_dismissals;
DELETE FROM flyway_schema_history WHERE version = '61';
COMMIT;
