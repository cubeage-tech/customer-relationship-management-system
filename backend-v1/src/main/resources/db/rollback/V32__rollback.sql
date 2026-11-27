-- Manual rollback for V32__add_service_support_features.sql. Assets, assignment rules, the support
-- form link and pending feedback links are lost; tickets themselves are kept.

BEGIN;
DROP TABLE IF EXISTS ticket_assignment_rules;
DROP INDEX IF EXISTS uq_tenants_support_form_token;
ALTER TABLE tenants DROP COLUMN IF EXISTS support_form_token;
DROP INDEX IF EXISTS idx_service_tickets_open_sla;
DROP INDEX IF EXISTS uq_service_tickets_feedback_token;
ALTER TABLE service_tickets DROP COLUMN IF EXISTS feedback_at;
ALTER TABLE service_tickets DROP COLUMN IF EXISTS feedback_token;
ALTER TABLE service_tickets DROP COLUMN IF EXISTS sla_breach_notified_at;
ALTER TABLE service_tickets DROP COLUMN IF EXISTS sla_at_risk_notified_at;
ALTER TABLE service_tickets DROP COLUMN IF EXISTS reporter_email;
ALTER TABLE service_tickets DROP COLUMN IF EXISTS source;
ALTER TABLE service_tickets DROP COLUMN IF EXISTS asset_id;
DROP TABLE IF EXISTS customer_assets;
DELETE FROM flyway_schema_history WHERE version = '32';
COMMIT;
