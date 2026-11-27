-- Manual rollback for V22__add_lead_team.sql. Leads lose their team assignment; owners are kept.

BEGIN;
DROP INDEX IF EXISTS idx_leads_tenant_team;
ALTER TABLE leads DROP COLUMN IF EXISTS team_id;
DELETE FROM flyway_schema_history WHERE version = '22';
COMMIT;
