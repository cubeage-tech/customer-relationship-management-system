// api.service.js
import ApiInterceptor from "./interceptor.service";
import ServerUrl from "../constants/serverUrl.constant";

class ApiService {
  // interceptor.service.js already handles auth headers, token expiry,
  // and 401 redirect — ApiService just consumes the configured instance.
  static axiosInstance = ApiInterceptor.init();

  // ------------------ Auth APIs ------------------
  static login(email, password) {
    return this.apipost(ServerUrl.AUTH_LOGIN, { email, password });
  }

  static signup(data) {
    return this.apipost(ServerUrl.AUTH_REGISTER, data);
  }

  static logout() {
    return this.apipost(ServerUrl.AUTH_LOGOUT);
  }

  static verifyEmail(data) {
    return this.apipost(ServerUrl.AUTH_VERIFY_EMAIL, data);
  }

  static forgotPassword(data) {
    return this.apipost(ServerUrl.AUTH_FORGOT_PASSWORD, data);
  }

  static resetPassword(data) {
    return this.apipost(ServerUrl.AUTH_RESET_PASSWORD, data);
  }

  // ------------------ User APIs ------------------
  static getUsers(params) {
    return this.apiget(ServerUrl.USERS, params);
  }

  static createUser(data) {
    return this.apipost(ServerUrl.USERS, data);
  }

  // ------------------ Customer APIs ------------------
  static getCustomers(params) {
    return this.apiget(ServerUrl.CUSTOMERS, params);
  }

  static getCustomer(id) {
    return this.apiget(ServerUrl.customer(id));
  }

  static createCustomer(data) {
    return this.apipost(ServerUrl.CUSTOMERS, data);
  }

  static updateCustomer(id, data) {
    return this.apiput(ServerUrl.customer(id), data);
  }

  static archiveCustomer(id) {
    return this.apipatch(ServerUrl.customerArchive(id));
  }

  static restoreCustomer(id) {
    return this.apipatch(ServerUrl.customerRestore(id));
  }

  static addCustomerContact(customerId, data) {
    return this.apipost(ServerUrl.customerContacts(customerId), data);
  }

  static updateCustomerContact(customerId, contactId, data) {
    return this.apiput(ServerUrl.customerContact(customerId, contactId), data);
  }

  static deleteCustomerContact(customerId, contactId) {
    return this.apidelete(ServerUrl.customerContact(customerId, contactId));
  }

  // ------------------ Lead APIs ------------------
  static getLeads(params) {
    return this.apiget(ServerUrl.LEADS, params);
  }

  static getLead(id) {
    return this.apiget(ServerUrl.lead(id));
  }

  static createLead(data) {
    return this.apipost(ServerUrl.LEADS, data);
  }

  static updateLead(id, data) {
    return this.apiput(ServerUrl.lead(id), data);
  }

  static deleteLead(id) {
    return this.apidelete(ServerUrl.lead(id));
  }

  static changeLeadStage(id, data) {
    return this.apipatch(ServerUrl.leadStage(id), data);
  }

  static assignLead(id, data) {
    return this.apipatch(ServerUrl.leadAssign(id), data);
  }

  // ------------------ Opportunity APIs ------------------
  static getOpportunities(params) {
    return this.apiget(ServerUrl.OPPORTUNITIES, params);
  }

  static getOpportunitySummary() {
    return this.apiget(ServerUrl.OPPORTUNITIES_SUMMARY);
  }

  static getOpportunity(id) {
    return this.apiget(ServerUrl.opportunity(id));
  }

  static createOpportunity(data) {
    return this.apipost(ServerUrl.OPPORTUNITIES, data);
  }

  static updateOpportunity(id, data) {
    return this.apiput(ServerUrl.opportunity(id), data);
  }

  static deleteOpportunity(id) {
    return this.apidelete(ServerUrl.opportunity(id));
  }

  static changeOpportunityStage(id, data) {
    return this.apipatch(ServerUrl.opportunityStage(id), data);
  }

  // ------------------ Quotation APIs ------------------
  static getQuotations(params) {
    return this.apiget(ServerUrl.QUOTATIONS, params);
  }

  static getQuotation(id) {
    return this.apiget(ServerUrl.quotation(id));
  }

  static createQuotation(data) {
    return this.apipost(ServerUrl.QUOTATIONS, data);
  }

  static updateQuotation(id, data) {
    return this.apiput(ServerUrl.quotation(id), data);
  }

  static sendQuotation(id) {
    return this.apipatch(ServerUrl.quotationSend(id));
  }

  static setQuotationCustomerStatus(id, data) {
    return this.apipatch(ServerUrl.quotationCustomerStatus(id), data);
  }

  static approveQuotationDiscount(id) {
    return this.apipatch(ServerUrl.quotationDiscountApprove(id));
  }

  static rejectQuotationDiscount(id, data) {
    return this.apipatch(ServerUrl.quotationDiscountReject(id), data);
  }

  /** Returns the raw axios response (blob) — a PDF download isn't wrapped in the JSON envelope. */
  static downloadQuotationPdf(id) {
    return this.axiosInstance.get(ServerUrl.quotationPdf(id), { responseType: "blob" });
  }

  // ------------------ Product APIs ------------------
  static getProducts() {
    return this.apiget(ServerUrl.PRODUCTS);
  }

  static createProduct(data) {
    return this.apipost(ServerUrl.PRODUCTS, data);
  }

  static updateProduct(id, data) {
    return this.apiput(ServerUrl.product(id), data);
  }

  static deactivateProduct(id) {
    return this.apipatch(ServerUrl.productDeactivate(id));
  }

  static activateProduct(id) {
    return this.apipatch(ServerUrl.productActivate(id));
  }

  // ------------------ Service Ticket APIs ------------------
  static getTickets(params) {
    return this.apiget(ServerUrl.TICKETS, params);
  }

  static getTicketSummary() {
    return this.apiget(ServerUrl.TICKETS_SUMMARY);
  }

  static getTicket(id) {
    return this.apiget(ServerUrl.ticket(id));
  }

  static createTicket(data) {
    return this.apipost(ServerUrl.TICKETS, data);
  }

  static updateTicket(id, data) {
    return this.apiput(ServerUrl.ticket(id), data);
  }

  static assignTicket(id, data) {
    return this.apipatch(ServerUrl.ticketAssign(id), data);
  }

  static changeTicketStatus(id, data) {
    return this.apipatch(ServerUrl.ticketStatus(id), data);
  }

  static recordTicketFeedback(id, data) {
    return this.apipatch(ServerUrl.ticketFeedback(id), data);
  }

  // ------------------ Campaign APIs ------------------
  static getCampaigns(params) {
    return this.apiget(ServerUrl.CAMPAIGNS, params);
  }

  static getCampaignSummary() {
    return this.apiget(ServerUrl.CAMPAIGNS_SUMMARY);
  }

  static getCampaign(id) {
    return this.apiget(ServerUrl.campaign(id));
  }

  static getCampaignLeads(id) {
    return this.apiget(ServerUrl.campaignLeads(id));
  }

  static createCampaign(data) {
    return this.apipost(ServerUrl.CAMPAIGNS, data);
  }

  static updateCampaign(id, data) {
    return this.apiput(ServerUrl.campaign(id), data);
  }

  static deleteCampaign(id) {
    return this.apidelete(ServerUrl.campaign(id));
  }

  static changeCampaignStatus(id, data) {
    return this.apipatch(ServerUrl.campaignStatus(id), data);
  }

  // ------------------ Tenant APIs ------------------
  static getTenants(params) {
    return this.apiget(ServerUrl.TENANTS, params);
  }

  static deactivateTenant(tenantId) {
    return this.apipatch(`${ServerUrl.TENANTS}/${tenantId}/deactivate`);
  }

  static restoreTenant(tenantId) {
    return this.apipatch(`${ServerUrl.TENANTS}/${tenantId}/restore`);
  }

  // ------------------ Plan APIs ------------------
  static listPlanPrices(params) {
    return this.apiget(ServerUrl.PLAN_PRICES, params);
  }

  static updatePlanPrice(plan, data) {
    return this.apiput(ServerUrl.planPrice(plan), data);
  }

  // ------------------ Super Admin APIs ------------------
  static getAllTenantsSuperAdmin(params) {
    return this.apiget(ServerUrl.ADMIN_TENANT, params);
  }

  static getSuperAdminDashboard() {
    return this.apiget(ServerUrl.ADMIN_DASHBOARD);
  }

  // ------------------ Generic Methods ------------------
  static apiget(url, params = {}) {
    return this.axiosInstance.get(url, { params }).then((res) => res.data?.data);
  }

  static apipost(url, body) {
    return this.axiosInstance.post(url, body).then((res) => res.data?.data);
  }

  static apiput(url, body) {
    return this.axiosInstance.put(url, body).then((res) => res.data?.data);
  }

  static apipatch(url, body) {
    return this.axiosInstance.patch(url, body).then((res) => res.data?.data);
  }

  static apidelete(url) {
    return this.axiosInstance.delete(url).then((res) => res.data?.data);
  }
}

export default ApiService;