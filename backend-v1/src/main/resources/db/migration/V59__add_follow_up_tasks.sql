-- FR-AI-7.3: follow-up tasks — things someone has to do for a customer, lead or deal, by a date.
-- Created by hand or from a meeting's action items (the AI proposes them, the user confirms).
-- The assignee is reminded once when a task falls due (due_notified_at).
CREATE TABLE follow_up_tasks (
    id               BIGSERIAL    PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL REFERENCES tenants(id),
    title            VARCHAR(200) NOT NULL,
    notes            TEXT,
    due_date         DATE,
    assignee_id      BIGINT       NOT NULL REFERENCES users(id),
    customer_id      BIGINT       REFERENCES customers(id),
    lead_id          BIGINT       REFERENCES leads(id) ON DELETE SET NULL,
    opportunity_id   BIGINT       REFERENCES opportunities(id) ON DELETE SET NULL,
    meeting_id       BIGINT       REFERENCES meetings(id) ON DELETE SET NULL,
    source           VARCHAR(20)  NOT NULL DEFAULT 'manual' CHECK (source IN ('manual', 'meeting_ai')),
    status           VARCHAR(20)  NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'done', 'cancelled')),
    completed_at     TIMESTAMP,
    due_notified_at  TIMESTAMP,
    created_by       BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_follow_up_tasks_assignee ON follow_up_tasks (tenant_id, assignee_id, status, due_date);
CREATE INDEX idx_follow_up_tasks_customer ON follow_up_tasks (tenant_id, customer_id) WHERE customer_id IS NOT NULL;
CREATE INDEX idx_follow_up_tasks_lead ON follow_up_tasks (tenant_id, lead_id) WHERE lead_id IS NOT NULL;
CREATE INDEX idx_follow_up_tasks_due ON follow_up_tasks (due_date) WHERE status = 'open' AND due_notified_at IS NULL;
