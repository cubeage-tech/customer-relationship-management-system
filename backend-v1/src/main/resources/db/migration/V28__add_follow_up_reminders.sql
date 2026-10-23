-- =============================================================================
-- FR-2.5: remind the lead's owner when a follow-up falls due. A scheduled job
-- picks leads whose follow_up_date has passed and that haven't been reminded for
-- that date (follow_up_notified_at IS NULL), emails the owner and — when enabled —
-- sends the FR-5.3 'follow_up_due' SMS alert. Changing the follow-up date clears
-- follow_up_notified_at so the new date is reminded too. Additive only.
-- Rollback: db/rollback/V28__rollback.sql.
-- =============================================================================

ALTER TABLE leads ADD COLUMN follow_up_notified_at TIMESTAMP;

CREATE INDEX idx_leads_follow_up_due ON leads(follow_up_date)
    WHERE follow_up_notified_at IS NULL AND follow_up_date IS NOT NULL;

-- FR-5.3: new SMS alert event (internal recipient only — a lead is not yet a customer).
ALTER TABLE sms_alert_rules DROP CONSTRAINT IF EXISTS sms_alert_rules_event_check;
ALTER TABLE sms_alert_rules ADD CONSTRAINT sms_alert_rules_event_check
    CHECK (event IN ('quotation_sent', 'ticket_status_changed', 'follow_up_due'));
