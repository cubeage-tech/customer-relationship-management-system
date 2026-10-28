-- FR-AI-6.1: the AI's reading of customer messages (email, WhatsApp, SMS) and of ticket feedback
-- (the customer's review): positive / neutral / negative, a score from -1 to 1, the churn risk and
-- a one-line reason. Classified as they arrive when the tenant switches it on, or on request.
ALTER TABLE communication_logs
    ADD COLUMN sentiment        VARCHAR(10) CHECK (sentiment IN ('positive', 'neutral', 'negative')),
    ADD COLUMN sentiment_score  NUMERIC(3, 2) CHECK (sentiment_score BETWEEN -1 AND 1),
    ADD COLUMN churn_risk       VARCHAR(10) CHECK (churn_risk IN ('low', 'medium', 'high')),
    ADD COLUMN sentiment_reason VARCHAR(300),
    ADD COLUMN sentiment_at     TIMESTAMP;

ALTER TABLE service_tickets
    ADD COLUMN feedback_sentiment        VARCHAR(10) CHECK (feedback_sentiment IN ('positive', 'neutral', 'negative')),
    ADD COLUMN feedback_sentiment_score  NUMERIC(3, 2) CHECK (feedback_sentiment_score BETWEEN -1 AND 1),
    ADD COLUMN feedback_churn_risk       VARCHAR(10) CHECK (feedback_churn_risk IN ('low', 'medium', 'high')),
    ADD COLUMN feedback_sentiment_reason VARCHAR(300),
    ADD COLUMN feedback_sentiment_at     TIMESTAMP;

-- Off by default: classifying as messages arrive sends their text to the AI provider (SEC-5).
ALTER TABLE tenants ADD COLUMN ai_sentiment_auto BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_communication_logs_sentiment ON communication_logs (tenant_id, customer_id, sentiment_at)
    WHERE sentiment IS NOT NULL;
