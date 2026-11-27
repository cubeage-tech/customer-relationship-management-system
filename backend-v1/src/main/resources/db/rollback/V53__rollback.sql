-- Manual rollback for V53__add_lead_budget_and_ai_score.sql. Lead budgets and stored AI scores are
-- lost (scores are recomputed by the job if the migration is applied again).

BEGIN;
DROP INDEX IF EXISTS idx_leads_tenant_ai_score;
ALTER TABLE leads DROP COLUMN IF EXISTS ai_scored_at;
ALTER TABLE leads DROP COLUMN IF EXISTS ai_score_label;
ALTER TABLE leads DROP COLUMN IF EXISTS ai_score;
ALTER TABLE leads DROP COLUMN IF EXISTS budget;
DELETE FROM flyway_schema_history WHERE version = '53';
COMMIT;
