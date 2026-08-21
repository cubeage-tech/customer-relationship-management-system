-- =============================================================================
-- PLAN PRICING
-- Lets a platform super_admin control what each subscription plan costs a
-- tenant admin to buy, without a backend redeploy.
-- =============================================================================

CREATE TABLE plan_prices (
  id BIGSERIAL PRIMARY KEY,
  plan VARCHAR(20) NOT NULL UNIQUE
    CHECK (plan IN ('starter', 'business', 'enterprise')),
  monthly_price NUMERIC(10, 2) NOT NULL,
  annual_price NUMERIC(10, 2) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'INR',
  updated_by BIGINT REFERENCES users(id),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO plan_prices (plan, monthly_price, annual_price, currency) VALUES
  ('starter', 1500.00, 1200.00, 'INR'),
  ('business', 3900.00, 3100.00, 'INR'),
  ('enterprise', 7100.00, 6300.00, 'INR');
