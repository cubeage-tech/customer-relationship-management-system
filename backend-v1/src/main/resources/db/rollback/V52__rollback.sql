-- Manual rollback for V52__add_password_history_call_log_push_retries.sql. Password history (reuse
-- check), the integration call log and pending push retries are lost.

BEGIN;
DROP TABLE IF EXISTS push_retries;
DROP TABLE IF EXISTS integration_calls;
DROP TABLE IF EXISTS password_history;
DELETE FROM flyway_schema_history WHERE version = '52';
COMMIT;
