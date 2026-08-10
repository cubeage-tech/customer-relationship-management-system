// src/core/constants/routes.constant.js
import { USER_ROLES } from './app.constant';

class RoutePath {
  // ==================== BASE ROUTES ====================
  static DASHBOARD_BASE = "/dashboard";
  static ADMIN_BASE = "/admin";

  // ==================== PUBLIC ROUTES ====================
  static HOME = "/";
  static ABOUT = "/about";
  static CONTACT = "/contact";
  static LOGIN = "/login";
  static SIGNUP = "/signup";
  static FORGOT_PASSWORD = "/forgot-password";

  // ======================================================
  // ROLE DASHBOARDS
  // ======================================================

  static ADMIN_DASHBOARD = `${this.DASHBOARD_BASE}/admin`;
  static SALES_MANAGER_DASHBOARD = `${this.DASHBOARD_BASE}/sales-manager`;
  static SALES_EXECUTIVE_DASHBOARD = `${this.DASHBOARD_BASE}/sales-executive`;
  static MARKETING_DASHBOARD = `${this.DASHBOARD_BASE}/marketing`;
  static SERVICE_DASHBOARD = `${this.DASHBOARD_BASE}/service`;
  static FINANCE_DASHBOARD = `${this.DASHBOARD_BASE}/finance`;
  static EXECUTIVE_DASHBOARD = `${this.DASHBOARD_BASE}/executive`;

  // ======================================================
  // CUSTOMERS / ACCOUNTS / CONTACTS
  // ======================================================

  static CUSTOMERS = "/customers";
  static ADD_CUSTOMER = "/customers/add";
  static EDIT_CUSTOMER = "/customers/:id";

  // ======================================================
  // LEADS
  // ======================================================

  static LEADS = "/leads";
  static ADD_LEAD = "/leads/add";
  static EDIT_LEAD = "/leads/:id";

  // ======================================================
  // OPPORTUNITIES / DEALS
  // ======================================================

  static OPPORTUNITIES = "/opportunities";
  static ADD_OPPORTUNITY = "/opportunities/add";
  static EDIT_OPPORTUNITY = "/opportunities/:id";

  // ======================================================
  // QUOTATIONS
  // ======================================================

  static QUOTATIONS = "/quotations";
  static ADD_QUOTATION = "/quotations/add";
  static EDIT_QUOTATION = "/quotations/:id";

  // ======================================================
  // SERVICE & SUPPORT
  // ======================================================

  static SERVICE_TICKETS = "/service-tickets";
  static ADD_SERVICE_TICKET = "/service-tickets/add";
  static EDIT_SERVICE_TICKET = "/service-tickets/:id";

  // ======================================================
  // MARKETING
  // ======================================================

  static CAMPAIGNS = "/campaigns";

  // ======================================================
  // FINANCE / APPROVALS
  // ======================================================

  static APPROVALS = "/approvals";

  // ======================================================
  // REPORTS
  // ======================================================

  static REPORTS = "/reports";

  // ======================================================
  // TENANT ADMINISTRATION
  // ======================================================

  static ADMIN_USERS = `${this.ADMIN_BASE}/users`;
  static ADMIN_ADD_USER = `${this.ADMIN_BASE}/users/add`;
  static ADMIN_EDIT_USER = `${this.ADMIN_BASE}/users/:id`;
  static ADMIN_ROLES = `${this.ADMIN_BASE}/roles`;
  static ADMIN_SETTINGS = `${this.ADMIN_BASE}/settings`;

  // ======================================================
  // COMMON
  // ======================================================

  static PROFILE = "/profile";

  static UNAUTHORIZED = "/unauthorized";

  static NOT_FOUND = "*";
}

/** Landing route after login, per CRM role. */
export const ROLE_HOME_ROUTE = {
  [USER_ROLES.SUPER_ADMIN]: RoutePath.ADMIN_DASHBOARD,
  [USER_ROLES.SALES_MANAGER]: RoutePath.SALES_MANAGER_DASHBOARD,
  [USER_ROLES.SALES_EXECUTIVE]: RoutePath.SALES_EXECUTIVE_DASHBOARD,
  [USER_ROLES.MARKETING_EXECUTIVE]: RoutePath.MARKETING_DASHBOARD,
  [USER_ROLES.SERVICE_AGENT]: RoutePath.SERVICE_DASHBOARD,
  [USER_ROLES.FINANCE_APPROVER]: RoutePath.FINANCE_DASHBOARD,
  [USER_ROLES.EXECUTIVE_OWNER]: RoutePath.EXECUTIVE_DASHBOARD,
};

export const getRoleHomeRoute = (role) => ROLE_HOME_ROUTE[role] || RoutePath.HOME;

export default RoutePath;
