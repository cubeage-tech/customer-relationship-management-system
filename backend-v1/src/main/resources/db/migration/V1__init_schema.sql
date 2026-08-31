-- =============================================================================
-- SmartCRM AI — Login & Registration Schema
-- Generated from the Auth module DBML. role_type is extended beyond the DBML's
-- "simplified for now" 2 values to the full set of CRM roles the product needs:
-- super_admin (platform) + admin (tenant owner) + 6 tenant team roles.
--
-- role_type / account_status / subscription_plan are modeled as VARCHAR +
-- CHECK constraints rather than native Postgres ENUM types, so they map
-- cleanly onto JPA AttributeConverters without enum-label casing friction.
-- =============================================================================

-- =============================================================================
-- TENANCY
-- =============================================================================

CREATE TABLE tenants (
  id BIGSERIAL PRIMARY KEY,
  company_name VARCHAR(255) NOT NULL,
  legal_name VARCHAR(255),
  plan VARCHAR(20) NOT NULL DEFAULT 'starter'
    CHECK (plan IN ('starter', 'business', 'enterprise')),
  status VARCHAR(30) NOT NULL DEFAULT 'active'
    CHECK (status IN ('pending_verification', 'active', 'suspended', 'deactivated')),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- =============================================================================
-- ROLES
-- =============================================================================

CREATE TABLE roles (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(30) NOT NULL UNIQUE
    CHECK (name IN (
      'super_admin', 'admin', 'sales_manager', 'sales_executive',
      'marketing_executive', 'service_agent', 'finance_approver', 'executive_owner'
    )),
  description VARCHAR(255)
);

-- =============================================================================
-- USERS
-- =============================================================================

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT REFERENCES tenants(id),
  role_id BIGINT NOT NULL REFERENCES roles(id),
  full_name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  phone VARCHAR(50),
  password_hash VARCHAR(255) NOT NULL,
  is_email_verified BOOLEAN NOT NULL DEFAULT false,
  status VARCHAR(30) NOT NULL DEFAULT 'pending_verification'
    CHECK (status IN ('pending_verification', 'active', 'suspended', 'deactivated')),
  mfa_enabled BOOLEAN NOT NULL DEFAULT false,
  last_login_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_tenant_id ON users(tenant_id);

-- =============================================================================
-- REGISTRATION / LOGIN SUPPORT
-- =============================================================================

CREATE TABLE email_verification_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  token VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  verified_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  token VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE login_attempts (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id),
  email_attempted VARCHAR(255),
  ip_address VARCHAR(64),
  is_successful BOOLEAN NOT NULL,
  failure_reason VARCHAR(50),
  attempted_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  token VARCHAR(500) NOT NULL UNIQUE,
  device_info VARCHAR(255),
  ip_address VARCHAR(64),
  expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);
