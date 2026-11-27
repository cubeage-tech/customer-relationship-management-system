-- Manual rollback for V33__add_marketing_execution.sql. Segments, send history, tracking data,
-- opt-outs and promotions are lost. Scheduled campaigns go back to draft.

BEGIN;
DROP TABLE IF EXISTS promotion_redemptions;
DROP TABLE IF EXISTS promotions;
DROP TABLE IF EXISTS campaign_recipients;
ALTER TABLE customers DROP COLUMN IF EXISTS marketing_opt_out_at;
ALTER TABLE customers DROP COLUMN IF EXISTS marketing_opt_out;
DROP INDEX IF EXISTS idx_campaigns_scheduled;
UPDATE campaigns SET status = 'draft' WHERE status = 'scheduled';
ALTER TABLE campaigns DROP COLUMN IF EXISTS message_body;
ALTER TABLE campaigns DROP COLUMN IF EXISTS message_subject;
ALTER TABLE campaigns DROP COLUMN IF EXISTS segment_id;
ALTER TABLE campaigns DROP COLUMN IF EXISTS launched_at;
ALTER TABLE campaigns DROP COLUMN IF EXISTS scheduled_at;
ALTER TABLE campaigns DROP CONSTRAINT IF EXISTS campaigns_status_check;
ALTER TABLE campaigns ADD CONSTRAINT campaigns_status_check
    CHECK (status IN ('draft', 'active', 'paused', 'completed', 'cancelled'));
DROP TABLE IF EXISTS customer_segments;
DELETE FROM flyway_schema_history WHERE version = '33';
COMMIT;
