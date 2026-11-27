-- NOT-5 / NOT-6 / NOT-8 / NOT-3: notifications for more events, admin-chosen channels per event,
-- and a delivery log of every notification sent.

-- In-app notifications for the new events; platform (super admin) notifications have no tenant (INT-8).
ALTER TABLE notifications ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('assignment', 'follow_up', 'message', 'sla_escalation', 'discount_approval', 'ai_alert', 'integration'));
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_entity_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_entity_type_check
    CHECK (entity_type IN ('lead', 'opportunity', 'quotation', 'service_ticket', 'customer', 'integration'));

-- NOT-6: which channels each event uses in a tenant. No row = the event's default. SMS stays in
-- sms_alert_rules (FR-5.3), which also holds who receives it.
CREATE TABLE notification_settings (
    tenant_id   BIGINT       NOT NULL REFERENCES tenants(id),
    event       VARCHAR(40)  NOT NULL,
    channel     VARCHAR(20)  NOT NULL CHECK (channel IN ('in_app', 'email', 'whatsapp')),
    enabled     BOOLEAN      NOT NULL,
    updated_by  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, event, channel)
);

-- NOT-8: one row per notification per channel. Messages to customers link to their
-- communication log row, whose status the provider keeps updating (sent → delivered / failed).
CREATE TABLE notification_deliveries (
    id                BIGSERIAL     PRIMARY KEY,
    tenant_id         BIGINT        REFERENCES tenants(id),
    event             VARCHAR(40)   NOT NULL,
    channel           VARCHAR(20)   NOT NULL CHECK (channel IN ('in_app', 'push', 'email', 'sms', 'whatsapp')),
    recipient_type    VARCHAR(20)   NOT NULL CHECK (recipient_type IN ('user', 'customer', 'lead')),
    recipient_id      BIGINT,
    recipient_name    VARCHAR(200),
    notification_id   BIGINT        REFERENCES notifications(id) ON DELETE SET NULL,
    communication_id  BIGINT        REFERENCES communication_logs(id) ON DELETE SET NULL,
    status            VARCHAR(20)   NOT NULL CHECK (status IN ('queued', 'sent', 'delivered', 'read', 'failed', 'skipped')),
    detail            VARCHAR(300),
    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_deliveries_tenant ON notification_deliveries(tenant_id, created_at DESC);

-- NOT-3: SMS on SLA escalation.
ALTER TABLE sms_alert_rules DROP CONSTRAINT IF EXISTS sms_alert_rules_event_check;
ALTER TABLE sms_alert_rules ADD CONSTRAINT sms_alert_rules_event_check
    CHECK (event IN ('quotation_sent', 'ticket_status_changed', 'follow_up_due', 'ticket_escalated'));
