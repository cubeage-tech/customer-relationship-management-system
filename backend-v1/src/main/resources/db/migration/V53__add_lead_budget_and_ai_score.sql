-- FR-AI-1.1: the lead's budget (a scoring factor). FR-AI-1.2/1.3: the lead's latest AI score and its
-- Low/Medium/High label, kept on the lead (refreshed when it changes and every 30 minutes) so the
-- lead list can show, filter and sort by it.

ALTER TABLE leads ADD COLUMN budget NUMERIC(14, 2) CHECK (budget >= 0);
ALTER TABLE leads ADD COLUMN ai_score INTEGER CHECK (ai_score BETWEEN 0 AND 100);
ALTER TABLE leads ADD COLUMN ai_score_label VARCHAR(10) CHECK (ai_score_label IN ('High', 'Medium', 'Low'));
ALTER TABLE leads ADD COLUMN ai_scored_at TIMESTAMP;

CREATE INDEX idx_leads_tenant_ai_score ON leads(tenant_id, ai_score DESC);
