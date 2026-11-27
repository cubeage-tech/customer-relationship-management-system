-- =============================================================================
-- FR-4.8: estimated profit margin on a quotation. Products get an optional cost
-- price; each line item keeps its own unit cost (prefilled from the product, or
-- entered for free-text lines) so a quotation's margin doesn't change when a
-- product's cost is edited later. Both nullable: unknown cost = no margin shown.
-- Additive only. Rollback: db/rollback/V27__rollback.sql.
-- =============================================================================

ALTER TABLE products ADD COLUMN cost_price NUMERIC(14, 2) CHECK (cost_price >= 0);
ALTER TABLE quotation_line_items ADD COLUMN unit_cost NUMERIC(14, 2) CHECK (unit_cost >= 0);
