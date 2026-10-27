-- Manual rollback for V41__add_custom_fields.sql. Custom field definitions and every stored
-- custom field value are lost.

BEGIN;
DROP TABLE IF EXISTS custom_field_values;
DROP TABLE IF EXISTS custom_field_definitions;
DELETE FROM flyway_schema_history WHERE version = '41';
COMMIT;
