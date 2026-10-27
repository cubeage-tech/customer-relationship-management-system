-- Manual rollback for V45__add_communication_consent.sql. SMS opt-outs and WhatsApp opt-ins are
-- lost, and WhatsApp campaigns become "other".

BEGIN;
UPDATE campaigns SET channel = 'other' WHERE channel = 'whatsapp';
ALTER TABLE campaigns DROP CONSTRAINT IF EXISTS campaigns_channel_check;
ALTER TABLE campaigns ADD CONSTRAINT campaigns_channel_check
    CHECK (channel IN ('email', 'social_media', 'sms', 'event', 'webinar', 'paid_ads', 'other'));
ALTER TABLE customers DROP COLUMN IF EXISTS consent_source;
ALTER TABLE customers DROP COLUMN IF EXISTS consent_updated_at;
ALTER TABLE customers DROP COLUMN IF EXISTS whatsapp_opt_in_at;
ALTER TABLE customers DROP COLUMN IF EXISTS whatsapp_opt_in;
ALTER TABLE customers DROP COLUMN IF EXISTS sms_opt_out_at;
ALTER TABLE customers DROP COLUMN IF EXISTS sms_opt_out;
DELETE FROM flyway_schema_history WHERE version = '45';
COMMIT;
