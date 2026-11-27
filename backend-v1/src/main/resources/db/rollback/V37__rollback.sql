-- Manual rollback for V37__add_mobile_app_support.sql. Visit purpose/outcome, meeting reports,
-- notifications and registered devices are lost.

BEGIN;
DROP TABLE IF EXISTS user_devices;
DROP TABLE IF EXISTS notifications;
ALTER TABLE meetings DROP COLUMN IF EXISTS reported_by;
ALTER TABLE meetings DROP COLUMN IF EXISTS reported_at;
ALTER TABLE meetings DROP COLUMN IF EXISTS next_steps;
ALTER TABLE meetings DROP COLUMN IF EXISTS report_notes;
ALTER TABLE meetings DROP COLUMN IF EXISTS report_outcome;
DROP INDEX IF EXISTS uq_sales_activities_client_ref;
ALTER TABLE sales_activities DROP COLUMN IF EXISTS client_ref;
ALTER TABLE sales_activities DROP COLUMN IF EXISTS outcome;
ALTER TABLE sales_activities DROP COLUMN IF EXISTS purpose;
DELETE FROM flyway_schema_history WHERE version = '37';
COMMIT;
