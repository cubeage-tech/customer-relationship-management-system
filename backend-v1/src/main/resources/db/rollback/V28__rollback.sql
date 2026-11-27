-- Manual rollback for V28__add_follow_up_reminders.sql. Follow-up SMS alert rules are removed.

BEGIN;
DELETE FROM sms_alert_rules WHERE event = 'follow_up_due';
ALTER TABLE sms_alert_rules DROP CONSTRAINT IF EXISTS sms_alert_rules_event_check;
ALTER TABLE sms_alert_rules ADD CONSTRAINT sms_alert_rules_event_check
    CHECK (event IN ('quotation_sent', 'ticket_status_changed'));
DROP INDEX IF EXISTS idx_leads_follow_up_due;
ALTER TABLE leads DROP COLUMN IF EXISTS follow_up_notified_at;
DELETE FROM flyway_schema_history WHERE version = '28';
COMMIT;
