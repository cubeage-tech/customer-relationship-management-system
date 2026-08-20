-- Fixed system/CRM roles. The bootstrap super_admin *user* is not seeded here
-- (needs a bcrypt hash, which SQL can't compute) — see
-- com.company.crm.common.config.DataSeeder, which creates it on startup using
-- the app's PasswordEncoder if no super_admin exists yet.

INSERT INTO roles (name, description) VALUES
  ('super_admin', 'Devniks/Cubeage platform staff — manage all tenant CRMs'),
  ('admin', 'Tenant owner/buyer — manages their own company CRM'),
  ('sales_manager', 'Manages a sales team and its pipeline'),
  ('sales_executive', 'Works leads, opportunities and quotations'),
  ('marketing_executive', 'Runs marketing campaigns'),
  ('service_agent', 'Handles customer service tickets'),
  ('finance_approver', 'Approves quotations and discounts'),
  ('executive_owner', 'Read-only cross-tenant executive visibility');
