-- Manual rollback for V35__add_dashboard_preferences.sql. Users get the default dashboard tiles again.

BEGIN;
DROP TABLE IF EXISTS dashboard_preferences;
DELETE FROM flyway_schema_history WHERE version = '35';
COMMIT;
