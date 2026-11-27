-- =============================================================================
-- FR-3.2: each tenant configures its sales stages. Seeded with the SRS list.
-- 'won' and 'lost' are fixed terminal stages (can be renamed, never removed);
-- every other code is an open stage that admins can add, rename, reorder and
-- deactivate. Codes never change, so existing opportunities keep their stage.
-- opportunities.stage stays a code but is no longer limited by a CHECK.
-- Rollback: db/rollback/V30__rollback.sql.
-- =============================================================================

CREATE TABLE opportunity_stages (
    tenant_id  BIGINT      NOT NULL REFERENCES tenants(id),
    code       VARCHAR(30) NOT NULL,
    label      VARCHAR(60) NOT NULL,
    sort_order INT         NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, code)
);

CREATE UNIQUE INDEX uq_opportunity_stages_tenant_label ON opportunity_stages(tenant_id, lower(label));

INSERT INTO opportunity_stages (tenant_id, code, label, sort_order)
SELECT t.id, s.code, s.label, s.sort_order
FROM tenants t
CROSS JOIN (VALUES ('qualification', 'Qualification', 1),
                   ('proposal', 'Proposal', 2),
                   ('negotiation', 'Negotiation', 3),
                   ('won', 'Won', 1000),
                   ('lost', 'Lost', 1001)) AS s(code, label, sort_order);

ALTER TABLE opportunities DROP CONSTRAINT IF EXISTS opportunities_stage_check;
ALTER TABLE opportunities ALTER COLUMN stage TYPE VARCHAR(30);
