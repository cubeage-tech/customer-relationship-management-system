-- Manual rollback for V42__add_tenant_role_permissions.sql. Every tenant's permission
-- restrictions are lost; all roles return to the built-in matrix.

BEGIN;
DROP TABLE IF EXISTS tenant_role_permission_revocations;
DELETE FROM flyway_schema_history WHERE version = '42';
COMMIT;
