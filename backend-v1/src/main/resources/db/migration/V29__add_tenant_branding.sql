-- =============================================================================
-- AC-4.2: company branding printed on quotation PDFs — logo (bytes in document
-- storage under logo_key), brand colour and contact details. All optional; a
-- tenant without branding gets the previous name-only header. Additive only.
-- Rollback: db/rollback/V29__rollback.sql (stored logo files must be removed separately).
-- =============================================================================

ALTER TABLE tenants ADD COLUMN brand_address VARCHAR(300);
ALTER TABLE tenants ADD COLUMN brand_phone VARCHAR(30);
ALTER TABLE tenants ADD COLUMN brand_email VARCHAR(150);
ALTER TABLE tenants ADD COLUMN brand_tax_id VARCHAR(30);
ALTER TABLE tenants ADD COLUMN brand_color VARCHAR(7) CHECK (brand_color ~ '^#[0-9a-fA-F]{6}$');
ALTER TABLE tenants ADD COLUMN logo_key VARCHAR(200);
ALTER TABLE tenants ADD COLUMN logo_content_type VARCHAR(30);
