-- Manual rollback for V49__add_quotation_customer_response.sql. Customer comments on quotations and
-- the "customer responded" notifications are lost (the quotation statuses stay).

BEGIN;
DELETE FROM notifications WHERE type = 'quotation_response';
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('assignment', 'follow_up', 'message', 'sla_escalation', 'discount_approval', 'ai_alert', 'integration'));
ALTER TABLE quotations DROP COLUMN IF EXISTS customer_responded_at;
ALTER TABLE quotations DROP COLUMN IF EXISTS customer_response_comment;
DELETE FROM flyway_schema_history WHERE version = '49';
COMMIT;
