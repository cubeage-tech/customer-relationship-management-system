-- Manual rollback for V55__add_sentiment.sql. The stored sentiment of messages and feedback is
-- lost; the messages and feedback themselves stay.

BEGIN;
DROP INDEX IF EXISTS idx_communication_logs_sentiment;
ALTER TABLE communication_logs
    DROP COLUMN IF EXISTS sentiment,
    DROP COLUMN IF EXISTS sentiment_score,
    DROP COLUMN IF EXISTS churn_risk,
    DROP COLUMN IF EXISTS sentiment_reason,
    DROP COLUMN IF EXISTS sentiment_at;
ALTER TABLE service_tickets
    DROP COLUMN IF EXISTS feedback_sentiment,
    DROP COLUMN IF EXISTS feedback_sentiment_score,
    DROP COLUMN IF EXISTS feedback_churn_risk,
    DROP COLUMN IF EXISTS feedback_sentiment_reason,
    DROP COLUMN IF EXISTS feedback_sentiment_at;
ALTER TABLE tenants DROP COLUMN IF EXISTS ai_sentiment_auto;
DELETE FROM flyway_schema_history WHERE version = '55';
COMMIT;
