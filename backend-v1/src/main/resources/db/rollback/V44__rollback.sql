-- Manual rollback for V44__add_notification_settings_and_deliveries.sql. Notification channel
-- settings, the delivery log and notifications of the new types are lost.

BEGIN;
DELETE FROM sms_alert_rules WHERE event = 'ticket_escalated';
ALTER TABLE sms_alert_rules DROP CONSTRAINT IF EXISTS sms_alert_rules_event_check;
ALTER TABLE sms_alert_rules ADD CONSTRAINT sms_alert_rules_event_check
    CHECK (event IN ('quotation_sent', 'ticket_status_changed', 'follow_up_due'));
DROP TABLE IF EXISTS notification_deliveries;
DROP TABLE IF EXISTS notification_settings;
DELETE FROM notifications WHERE tenant_id IS NULL
    OR type NOT IN ('assignment', 'follow_up', 'message')
    OR entity_type NOT IN ('lead', 'opportunity');
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_entity_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_entity_type_check CHECK (entity_type IN ('lead', 'opportunity'));
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN ('assignment', 'follow_up', 'message'));
ALTER TABLE notifications ALTER COLUMN tenant_id SET NOT NULL;
DELETE FROM flyway_schema_history WHERE version = '44';
COMMIT;
