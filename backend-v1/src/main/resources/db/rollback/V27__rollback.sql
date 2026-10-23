-- Manual rollback for V27__add_cost_for_quotation_margin.sql. Product costs and line-item costs are lost.

BEGIN;
ALTER TABLE quotation_line_items DROP COLUMN IF EXISTS unit_cost;
ALTER TABLE products DROP COLUMN IF EXISTS cost_price;
DELETE FROM flyway_schema_history WHERE version = '27';
COMMIT;
