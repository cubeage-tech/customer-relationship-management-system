-- =============================================================================
-- FR-1.3 (customer purchase history): a customer-approved quotation counts as a
-- purchase, dated when the approval was recorded. updated_at moves on every later
-- edit, so the approval moment gets its own column. Additive and nullable.
-- Existing approved quotations are backfilled with updated_at (best available).
-- Rollback: db/rollback/V20__rollback.sql.
-- =============================================================================

ALTER TABLE quotations ADD COLUMN customer_approved_at TIMESTAMP;

UPDATE quotations SET customer_approved_at = updated_at WHERE status = 'approved';
