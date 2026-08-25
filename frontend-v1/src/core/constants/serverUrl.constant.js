class ServerUrl {
  static BASE_URL = import.meta.env.VITE_API_URL;

  // auth module
  static API_MODULE_AUTH = "/auth";
  static AUTH_LOGIN = ServerUrl.API_MODULE_AUTH + "/login";
  static AUTH_REGISTER = ServerUrl.API_MODULE_AUTH + "/signup";
  static AUTH_LOGOUT = ServerUrl.API_MODULE_AUTH + "/logout";
  static AUTH_VERIFY_EMAIL = ServerUrl.API_MODULE_AUTH + "/verify-email";
  static AUTH_FORGOT_PASSWORD = ServerUrl.API_MODULE_AUTH + "/forgot-password";
  static AUTH_RESET_PASSWORD = ServerUrl.API_MODULE_AUTH + "/reset-password";

  static API_MODULE_USER = "/api";
  static USERS = ServerUrl.API_MODULE_USER + "/users";
  static TENANTS = ServerUrl.API_MODULE_USER + "/tenants";
  static ADMIN_TENANT = ServerUrl.API_MODULE_USER + "/super-admin/tenants";
  static ADMIN_DASHBOARD = ServerUrl.API_MODULE_USER + "/dashboard/super-admin";

  // plans
  static PLANS = ServerUrl.API_MODULE_USER + "/plans";
  static PLAN_PRICES = ServerUrl.PLANS + "/prices"; // GET list
  static planPrice = (plan) => `${ServerUrl.PLANS}/${plan}/price`; // PUT single
}

export default ServerUrl;