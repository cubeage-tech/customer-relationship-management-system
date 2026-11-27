-- FR-AI-6.2: each customer's churn risk (low / medium / high, score 0–100) and the reasons, worked
-- out inside the CRM from unhappy messages and reviews, open problems, going quiet and lost deals.
-- churn_flagged_at: when the owner and their manager were last told it turned high (told once per rise).
ALTER TABLE customers
    ADD COLUMN churn_risk        VARCHAR(10) CHECK (churn_risk IN ('low', 'medium', 'high')),
    ADD COLUMN churn_score       INTEGER CHECK (churn_score BETWEEN 0 AND 100),
    ADD COLUMN churn_reasons     TEXT,
    ADD COLUMN churn_assessed_at TIMESTAMP,
    ADD COLUMN churn_flagged_at  TIMESTAMP;

CREATE INDEX idx_customers_churn ON customers (tenant_id, churn_score DESC) WHERE churn_risk IN ('medium', 'high');
