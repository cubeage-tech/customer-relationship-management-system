-- NAV-2: the customer accepts or declines a quotation from its view link (with an optional comment),
-- and the owner is told in-app.

ALTER TABLE quotations ADD COLUMN customer_response_comment VARCHAR(1000);
ALTER TABLE quotations ADD COLUMN customer_responded_at TIMESTAMP;

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('assignment', 'follow_up', 'message', 'sla_escalation', 'discount_approval', 'ai_alert', 'integration',
                    'quotation_response'));
