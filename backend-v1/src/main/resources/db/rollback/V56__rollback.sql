-- Manual rollback for V56__add_customer_churn_risk.sql. The stored churn risk is lost; it is
-- recalculated from the CRM's data if the migration is applied again.

BEGIN;
DROP INDEX IF EXISTS idx_customers_churn;
ALTER TABLE customers
    DROP COLUMN IF EXISTS churn_risk,
    DROP COLUMN IF EXISTS churn_score,
    DROP COLUMN IF EXISTS churn_reasons,
    DROP COLUMN IF EXISTS churn_assessed_at,
    DROP COLUMN IF EXISTS churn_flagged_at;
DELETE FROM flyway_schema_history WHERE version = '56';
COMMIT;
