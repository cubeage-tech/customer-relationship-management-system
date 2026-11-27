-- NOT-7: a customer's communication preferences. Marketing email opt-out already exists
-- (marketing_opt_out, FR-7.1c). SMS is allowed until the customer opts out (STOP); WhatsApp
-- needs an explicit opt-in. consent_source records how the last change was made.
-- NOT-2: WhatsApp campaigns (promotions to opted-in customers).

ALTER TABLE customers ADD COLUMN sms_opt_out BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE customers ADD COLUMN sms_opt_out_at TIMESTAMP;
ALTER TABLE customers ADD COLUMN whatsapp_opt_in BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE customers ADD COLUMN whatsapp_opt_in_at TIMESTAMP;
ALTER TABLE customers ADD COLUMN consent_updated_at TIMESTAMP;
ALTER TABLE customers ADD COLUMN consent_source VARCHAR(40);

ALTER TABLE campaigns DROP CONSTRAINT IF EXISTS campaigns_channel_check;
ALTER TABLE campaigns ADD CONSTRAINT campaigns_channel_check
    CHECK (channel IN ('email', 'social_media', 'sms', 'whatsapp', 'event', 'webinar', 'paid_ads', 'other'));
