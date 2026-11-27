-- =============================================================================
-- Bank account number captured on the public signup form (billing/payout
-- settlement for the new tenant). Nullable: existing tenants predate the
-- field and have no value to backfill; new signups are required to supply
-- one at the application layer (see AdminSignupRequest validation).
-- =============================================================================

ALTER TABLE tenants ADD COLUMN bank_account_number VARCHAR(18);
