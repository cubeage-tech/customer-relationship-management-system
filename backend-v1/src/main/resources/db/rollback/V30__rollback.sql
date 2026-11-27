-- Manual rollback for V30__add_tenant_opportunity_stages.sql. Opportunities in a custom stage
-- are moved back to 'qualification' so the original CHECK constraint can be restored.

BEGIN;
UPDATE opportunities SET stage = 'qualification'
WHERE stage NOT IN ('qualification', 'proposal', 'negotiation', 'won', 'lost');
ALTER TABLE opportunities ALTER COLUMN stage TYPE VARCHAR(20);
ALTER TABLE opportunities ADD CONSTRAINT opportunities_stage_check
    CHECK (stage IN ('qualification', 'proposal', 'negotiation', 'won', 'lost'));
DROP TABLE IF EXISTS opportunity_stages;
DELETE FROM flyway_schema_history WHERE version = '30';
COMMIT;
