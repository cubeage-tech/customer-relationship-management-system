-- =============================================================================
-- SaaS subscriptions
-- `plans` becomes the source of truth for what each tier includes (limits);
-- tenants.plan is kept as a denormalized cache of the tenant's current plan
-- (kept in sync by SubscriptionService) so existing dashboards keep working.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- PLANS — NULL limit = unlimited
-- -----------------------------------------------------------------------------

CREATE TABLE plans (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  max_users INTEGER,
  max_customers INTEGER,
  max_campaigns INTEGER,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO plans (code, name, description, max_users, max_customers, max_campaigns) VALUES
  ('starter', 'Starter', 'For small teams getting started', 5, 500, 5),
  ('business', 'Business', 'For growing sales and marketing teams', 25, 5000, 50),
  ('enterprise', 'Enterprise', 'Unlimited users, customers and campaigns', NULL, NULL, NULL);

-- -----------------------------------------------------------------------------
-- PLAN PRICES — re-point from the plan code column to a plans(id) foreign key.
-- Every existing row is backfilled before the old column is dropped.
-- -----------------------------------------------------------------------------

ALTER TABLE plan_prices ADD COLUMN plan_id BIGINT REFERENCES plans(id);

UPDATE plan_prices pp SET plan_id = p.id FROM plans p WHERE p.code = pp.plan;

ALTER TABLE plan_prices ALTER COLUMN plan_id SET NOT NULL;
ALTER TABLE plan_prices ADD CONSTRAINT uq_plan_prices_plan_id UNIQUE (plan_id);
ALTER TABLE plan_prices DROP COLUMN plan;

-- -----------------------------------------------------------------------------
-- PAYMENTS — one row per checkout attempt with the payment provider
-- -----------------------------------------------------------------------------

CREATE TABLE payments (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  user_id BIGINT NOT NULL REFERENCES users(id),
  plan_id BIGINT NOT NULL REFERENCES plans(id),
  provider VARCHAR(30) NOT NULL,
  billing_cycle VARCHAR(20) NOT NULL
    CHECK (billing_cycle IN ('monthly', 'annual')),
  amount NUMERIC(14, 2) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'INR',
  status VARCHAR(20) NOT NULL DEFAULT 'created'
    CHECK (status IN ('created', 'paid', 'failed')),
  gateway_order_id VARCHAR(100) NOT NULL UNIQUE,
  gateway_payment_id VARCHAR(100),
  gateway_signature VARCHAR(500),
  failure_reason VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_tenant ON payments(tenant_id);
CREATE INDEX idx_payments_user ON payments(user_id);
CREATE INDEX idx_payments_payment ON payments(gateway_payment_id);

-- -----------------------------------------------------------------------------
-- SUBSCRIPTIONS — exactly one current subscription per tenant (billing is per
-- tenant). Renewals/upgrades update this row; the payment history lives in
-- `payments`.
-- -----------------------------------------------------------------------------

CREATE TABLE subscriptions (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL UNIQUE REFERENCES tenants(id),
  plan_id BIGINT NOT NULL REFERENCES plans(id),
  payment_id BIGINT REFERENCES payments(id),
  billing_cycle VARCHAR(20)
    CHECK (billing_cycle IN ('monthly', 'annual')),
  status VARCHAR(20) NOT NULL
    CHECK (status IN ('trial', 'active', 'past_due', 'cancelled', 'expired')),
  started_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  cancelled_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscriptions_status_expires ON subscriptions(status, expires_at);

-- Existing tenants predate billing: grandfather them as active on their current
-- plan for 30 days so nobody is locked out by this deploy.
INSERT INTO subscriptions (tenant_id, plan_id, status, started_at, expires_at)
SELECT t.id, p.id, 'active', now(), now() + INTERVAL '30 days'
FROM tenants t
JOIN plans p ON p.code = t.plan;
