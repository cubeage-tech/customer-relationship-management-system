-- =============================================================================
-- Tenant "My Plan" page
-- display_order ranks plans (higher = bigger tier) so "is this an upgrade?" is
-- data, not code. plan_features holds the bullet list shown for each plan.
-- =============================================================================

ALTER TABLE plans ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;

UPDATE plans SET display_order = 1 WHERE code = 'starter';
UPDATE plans SET display_order = 2 WHERE code = 'business';
UPDATE plans SET display_order = 3 WHERE code = 'enterprise';

CREATE TABLE plan_features (
  plan_id BIGINT NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
  sort_order INTEGER NOT NULL,
  feature VARCHAR(200) NOT NULL,
  PRIMARY KEY (plan_id, sort_order)
);

-- Every module is available on every plan today (plans differ by limits, which the
-- page shows separately), so the lists only name what the product actually does.
INSERT INTO plan_features (plan_id, sort_order, feature)
SELECT p.id, f.sort_order, f.feature
FROM plans p
CROSS JOIN (VALUES
  (0, 'Customer 360, leads and sales pipeline'),
  (1, 'Quotations with PDF proposals and discount approvals'),
  (2, 'Service tickets with SLA tracking'),
  (3, 'Marketing campaigns with lead attribution'),
  (4, 'Role-based access for your whole team')
) AS f(sort_order, feature)
WHERE p.code IN ('starter', 'business', 'enterprise');

INSERT INTO plan_features (plan_id, sort_order, feature)
SELECT id, 5, 'Unlimited users, customers and campaigns' FROM plans WHERE code = 'enterprise';
