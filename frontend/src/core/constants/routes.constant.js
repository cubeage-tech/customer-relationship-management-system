// src/core/constants/routes.constant.js
import { USER_ROLES } from './app.constant';

class RoutePath {
  // ==================== BASE ROUTES ====================
  static SUPER_ADMIN_BASE = "/admin";
  static INDUSTRY_BASE = "/industry";
  static DEALER_BASE = "/dealer";
  static BUYER_BASE = "/buyer";

  // ==================== PUBLIC ROUTES ====================
  static HOME = "/";
  static ABOUT = "/about";
  static CONTACT = "/contact";
  static LOGIN = "/login";
  static SIGNUP = "/signup";
  static FORGOT_PASSWORD = "/forgot-password";

  // ======================================================
  // SUPER ADMIN
  // ======================================================

  static ADMIN_DASHBOARD = `${this.SUPER_ADMIN_BASE}/dashboard`;

  static ADMIN_INDUSTRIES = `${this.SUPER_ADMIN_BASE}/industries`;
  static ADMIN_ADD_INDUSTRY = `${this.SUPER_ADMIN_BASE}/industries/add`;
  static ADMIN_EDIT_INDUSTRY = `${this.SUPER_ADMIN_BASE}/industries/:id`;

  static ADMIN_DEALERS = `${this.SUPER_ADMIN_BASE}/dealers`;
  static ADMIN_BUYERS = `${this.SUPER_ADMIN_BASE}/buyers`;

  static ADMIN_SCRAP = `${this.SUPER_ADMIN_BASE}/scrap`;
  static ADMIN_CATEGORIES = `${this.SUPER_ADMIN_BASE}/categories`;

  static ADMIN_AUCTIONS = `${this.SUPER_ADMIN_BASE}/auctions`;

  static ADMIN_TRANSPORT = `${this.SUPER_ADMIN_BASE}/transport`;

  static ADMIN_PAYMENTS = `${this.SUPER_ADMIN_BASE}/payments`;

  static ADMIN_REPORTS = `${this.SUPER_ADMIN_BASE}/reports`;

  static ADMIN_SETTINGS = `${this.SUPER_ADMIN_BASE}/settings`;

  static ADMIN_PROFILE = `${this.SUPER_ADMIN_BASE}/profile`;

  // ======================================================
  // INDUSTRY
  // ======================================================

  static INDUSTRY_DASHBOARD = `${this.INDUSTRY_BASE}/dashboard`;

  static INDUSTRY_MY_SCRAP = `${this.INDUSTRY_BASE}/scrap`;

  static INDUSTRY_ADD_SCRAP = `${this.INDUSTRY_BASE}/scrap/add`;

  static INDUSTRY_EDIT_SCRAP = `${this.INDUSTRY_BASE}/scrap/:id`;

  static INDUSTRY_AUCTIONS = `${this.INDUSTRY_BASE}/auctions`;

  static INDUSTRY_ORDERS = `${this.INDUSTRY_BASE}/orders`;

  static INDUSTRY_TRANSPORT = `${this.INDUSTRY_BASE}/transport`;

  static INDUSTRY_PAYMENTS = `${this.INDUSTRY_BASE}/payments`;

  static INDUSTRY_REPORTS = `${this.INDUSTRY_BASE}/reports`;

  static INDUSTRY_PROFILE = `${this.INDUSTRY_BASE}/profile`;

  // ======================================================
  // DEALER
  // ======================================================

  static DEALER_DASHBOARD = `${this.DEALER_BASE}/dashboard`;

  static DEALER_MARKETPLACE = `${this.DEALER_BASE}/marketplace`;

  static DEALER_AUCTIONS = `${this.DEALER_BASE}/auctions`;

  static DEALER_MY_BIDS = `${this.DEALER_BASE}/bids`;

  static DEALER_ORDERS = `${this.DEALER_BASE}/orders`;

  static DEALER_TRANSPORT = `${this.DEALER_BASE}/transport`;

  static DEALER_INVOICES = `${this.DEALER_BASE}/invoices`;

  static DEALER_PROFILE = `${this.DEALER_BASE}/profile`;

  // ======================================================
  // BUYER
  // ======================================================

  static BUYER_DASHBOARD = `${this.BUYER_BASE}/dashboard`;

  static BUYER_MARKETPLACE = `${this.BUYER_BASE}/marketplace`;

  static BUYER_AUCTIONS = `${this.BUYER_BASE}/auctions`;

  static BUYER_PURCHASES = `${this.BUYER_BASE}/purchases`;

  static BUYER_TRANSPORT = `${this.BUYER_BASE}/transport`;

  static BUYER_PAYMENTS = `${this.BUYER_BASE}/payments`;

  static BUYER_PROFILE = `${this.BUYER_BASE}/profile`;

  // ======================================================
  // COMMON
  // ======================================================

  static UNAUTHORIZED = "/unauthorized";

  static NOT_FOUND = "*";
}

export const ROLE_HOME_ROUTE = {
  [USER_ROLES.SUPER_ADMIN]: RoutePath.ADMIN_DASHBOARD,
  [USER_ROLES.INDUSTRY]: RoutePath.INDUSTRY_DASHBOARD,
  [USER_ROLES.DEALER]: RoutePath.DEALER_DASHBOARD,
  [USER_ROLES.BUYER]: RoutePath.BUYER_DASHBOARD,
};

export default RoutePath;