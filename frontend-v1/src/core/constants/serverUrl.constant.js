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

  // customers
  static CUSTOMERS = ServerUrl.API_MODULE_USER + "/customers";
  static customer = (id) => `${ServerUrl.CUSTOMERS}/${id}`;
  static customerArchive = (id) => `${ServerUrl.CUSTOMERS}/${id}/archive`;
  static customerRestore = (id) => `${ServerUrl.CUSTOMERS}/${id}/restore`;
  static customerContacts = (customerId) => `${ServerUrl.CUSTOMERS}/${customerId}/contacts`;
  static customerContact = (customerId, contactId) =>
    `${ServerUrl.CUSTOMERS}/${customerId}/contacts/${contactId}`;

  // leads
  static LEADS = ServerUrl.API_MODULE_USER + "/leads";
  static lead = (id) => `${ServerUrl.LEADS}/${id}`;
  static leadStage = (id) => `${ServerUrl.LEADS}/${id}/stage`;
  static leadAssign = (id) => `${ServerUrl.LEADS}/${id}/assign`;

  // opportunities
  static OPPORTUNITIES = ServerUrl.API_MODULE_USER + "/opportunities";
  static OPPORTUNITIES_SUMMARY = ServerUrl.OPPORTUNITIES + "/summary";
  static opportunity = (id) => `${ServerUrl.OPPORTUNITIES}/${id}`;
  static opportunityStage = (id) => `${ServerUrl.OPPORTUNITIES}/${id}/stage`;

  // quotations
  static QUOTATIONS = ServerUrl.API_MODULE_USER + "/quotations";
  static quotation = (id) => `${ServerUrl.QUOTATIONS}/${id}`;
  static quotationPdf = (id) => `${ServerUrl.QUOTATIONS}/${id}/pdf`;
  static quotationSend = (id) => `${ServerUrl.QUOTATIONS}/${id}/send`;
  static quotationCustomerStatus = (id) => `${ServerUrl.QUOTATIONS}/${id}/customer-status`;
  static quotationDiscountApprove = (id) => `${ServerUrl.QUOTATIONS}/${id}/discount/approve`;
  static quotationDiscountReject = (id) => `${ServerUrl.QUOTATIONS}/${id}/discount/reject`;

  // products (quotation price list)
  static PRODUCTS = ServerUrl.API_MODULE_USER + "/products";
  static product = (id) => `${ServerUrl.PRODUCTS}/${id}`;
  static productDeactivate = (id) => `${ServerUrl.PRODUCTS}/${id}/deactivate`;
  static productActivate = (id) => `${ServerUrl.PRODUCTS}/${id}/activate`;

  static ADMIN_TENANT = ServerUrl.API_MODULE_USER + "/super-admin/tenants";
  static ADMIN_DASHBOARD = ServerUrl.API_MODULE_USER + "/dashboard/super-admin";

  // plans
  static PLANS = ServerUrl.API_MODULE_USER + "/plans";
  static PLAN_PRICES = ServerUrl.PLANS + "/prices"; // GET list
  static planPrice = (plan) => `${ServerUrl.PLANS}/${plan}/price`; // PUT single
}

export default ServerUrl;