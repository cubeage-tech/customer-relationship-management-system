// Customer Relationship Management (CRM) — application-wide constants

export const APP_NAME = 'Customer Relationship Management';

export const APP_SHORT_NAME = 'CRM';

export const USER_ROLES = {
  SUPER_ADMIN: 'super_admin',
  SALES_MANAGER: 'sales_manager',
  SALES_EXECUTIVE: 'sales_executive',
  MARKETING_EXECUTIVE: 'marketing_executive',
  SERVICE_AGENT: 'service_agent',
  FINANCE_APPROVER: 'finance_approver',
  EXECUTIVE_OWNER: 'executive_owner',
};

/** Human readable name for every internal role name. */
export const ROLE_LABELS = {
  [USER_ROLES.SUPER_ADMIN]: 'Tenant Administrator',
  [USER_ROLES.SALES_MANAGER]: 'Sales Manager',
  [USER_ROLES.SALES_EXECUTIVE]: 'Sales Executive',
  [USER_ROLES.MARKETING_EXECUTIVE]: 'Marketing Executive',
  [USER_ROLES.SERVICE_AGENT]: 'Service Agent',
  [USER_ROLES.FINANCE_APPROVER]: 'Finance/Approver',
  [USER_ROLES.EXECUTIVE_OWNER]: 'Executive/Owner',
};

export const ROLE_DESCRIPTIONS = {
  [USER_ROLES.SUPER_ADMIN]:
    'Full access to every module, all teams and tenant administration settings.',
  [USER_ROLES.SALES_MANAGER]:
    'Manages the sales team: full access to team customers, leads and opportunities, approves quotations.',
  [USER_ROLES.SALES_EXECUTIVE]:
    'Works their own pipeline: owns leads and opportunities, creates quotations and service requests.',
  [USER_ROLES.MARKETING_EXECUTIVE]:
    'Runs campaigns and works marketing leads. Read-only on customers, no quotation access.',
  [USER_ROLES.SERVICE_AGENT]:
    'Handles assigned service tickets and views customer records for support context.',
  [USER_ROLES.FINANCE_APPROVER]:
    'Reviews revenue and approves quotation discounts. Read-only on customers and pipeline.',
  [USER_ROLES.EXECUTIVE_OWNER]:
    'Organisation-wide read access with full reporting and dashboards.',
};

/** Ordered list used by role dropdowns and role selection forms. */
export const ROLE_OPTIONS = Object.values(USER_ROLES).map((value) => ({
  value,
  label: ROLE_LABELS[value],
}));

/** Roles a user may pick during self-registration (tenant admin sets the rest). */
export const SIGNUP_ROLES = [
  USER_ROLES.SUPER_ADMIN,
  USER_ROLES.SALES_MANAGER,
  USER_ROLES.SALES_EXECUTIVE,
  USER_ROLES.MARKETING_EXECUTIVE,
  USER_ROLES.SERVICE_AGENT,
];

export const isValidRole = (role) => Object.values(USER_ROLES).includes(role);

export const STORAGE_KEYS = {
  AUTH_TOKEN: 'crm_auth_token',
  AUTH_USER: 'crm_auth_user',
};
