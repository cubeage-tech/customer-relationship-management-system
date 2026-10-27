-- Manual rollback for V47__add_integration_health.sql. The record of rejected integration
-- credentials is lost.

BEGIN;
DROP TABLE IF EXISTS integration_health;
DELETE FROM flyway_schema_history WHERE version = '47';
COMMIT;
