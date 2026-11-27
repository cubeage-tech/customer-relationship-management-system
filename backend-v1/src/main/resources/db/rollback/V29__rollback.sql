-- Manual rollback for V29__add_tenant_branding.sql. Branding is lost; logo files under
-- <document storage>/branding/ can be deleted by hand.

BEGIN;
ALTER TABLE tenants DROP COLUMN IF EXISTS logo_content_type;
ALTER TABLE tenants DROP COLUMN IF EXISTS logo_key;
ALTER TABLE tenants DROP COLUMN IF EXISTS brand_color;
ALTER TABLE tenants DROP COLUMN IF EXISTS brand_tax_id;
ALTER TABLE tenants DROP COLUMN IF EXISTS brand_email;
ALTER TABLE tenants DROP COLUMN IF EXISTS brand_phone;
ALTER TABLE tenants DROP COLUMN IF EXISTS brand_address;
DELETE FROM flyway_schema_history WHERE version = '29';
COMMIT;
