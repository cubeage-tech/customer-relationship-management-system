-- BIZ-2: the tenant's industry edition (SRS 2.1 / 25.3). Choosing one adds that industry's custom
-- fields (ARC-9 templates) and pipeline stages on top of the common core; nothing is removed.

ALTER TABLE tenants ADD COLUMN edition VARCHAR(30) NOT NULL DEFAULT 'standard'
    CHECK (edition IN ('standard', 'manufacturing', 'real_estate', 'healthcare', 'industrial_sales'));
