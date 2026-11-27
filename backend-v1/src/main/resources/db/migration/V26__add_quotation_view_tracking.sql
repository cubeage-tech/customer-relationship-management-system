-- =============================================================================
-- FR-4.5: track the customer's response automatically. Sending a quotation gives
-- it a secret view_token; the customer's link (/q/<token>) marks it 'viewed' the
-- first time it is opened (viewed_at). An hourly job moves pending/viewed
-- quotations whose valid_until has passed to 'expired'. Additive only.
-- Rollback: db/rollback/V26__rollback.sql.
-- =============================================================================

ALTER TABLE quotations ADD COLUMN view_token VARCHAR(64);
ALTER TABLE quotations ADD COLUMN viewed_at TIMESTAMP;

CREATE UNIQUE INDEX uq_quotations_view_token ON quotations(view_token) WHERE view_token IS NOT NULL;
CREATE INDEX idx_quotations_open_valid_until ON quotations(valid_until) WHERE status IN ('pending', 'viewed');
