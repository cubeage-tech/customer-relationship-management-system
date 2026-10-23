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

  static resendVerification(data) {
    return this.apipost(ServerUrl.AUTH_RESEND_VERIFICATION, data);
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

  static getCustomerPurchases(id) {
    return this.apiget(ServerUrl.customerPurchases(id));
  }

  static getCustomerDocuments(id) {
    return this.apiget(ServerUrl.customerDocuments(id));
  }

  // AC-4.2: company branding for quotation PDFs
  static getBranding() {
    return this.apiget(ServerUrl.BRANDING);
  }

  static updateBranding(data) {
    return this.apiput(ServerUrl.BRANDING, data);
  }

  static uploadBrandingLogo(file) {
    const form = new FormData();
    form.append('file', file);
    return this.axiosInstance.post(ServerUrl.BRANDING_LOGO, form).then((res) => res.data?.data);
  }

  static removeBrandingLogo() {
    return this.apidelete(ServerUrl.BRANDING_LOGO);
  }

  static getBrandingLogo() {
    return this.axiosInstance.get(ServerUrl.BRANDING_LOGO, { responseType: 'blob' });
  }

  static uploadCustomerDocument(id, file) {
    const form = new FormData();
    form.append('file', file);
    return this.axiosInstance.post(ServerUrl.customerDocuments(id), form).then((res) => res.data?.data);
  }

  static downloadCustomerDocument(id, documentId, inline = false) {
    return this.axiosInstance.get(`${ServerUrl.customerDocument(id, documentId)}/download`, {
      params: { inline },
      responseType: 'blob',
    });
  }

  static deleteCustomerDocument(id, documentId) {
    return this.apidelete(ServerUrl.customerDocument(id, documentId));
  }

  // ------------------ Communication APIs (Module 5) ------------------
  // FR-6.1 assets, support form; FR-6.3b ticket rules; FR-6.5 feedback
  static getCustomerAssets(customerId) {
    return this.apiget(ServerUrl.customerAssets(customerId));
  }

  static createCustomerAsset(customerId, data) {
    return this.apipost(ServerUrl.customerAssets(customerId), data);
  }

  static updateCustomerAsset(customerId, assetId, data) {
    return this.apiput(ServerUrl.customerAsset(customerId, assetId), data);
  }

  static deleteCustomerAsset(customerId, assetId) {
    return this.apidelete(ServerUrl.customerAsset(customerId, assetId));
  }

  static getTicketAssignmentRules() {
    return this.apiget(ServerUrl.TICKET_ASSIGNMENT_RULES);
  }

  static createTicketAssignmentRule(data) {
    return this.apipost(ServerUrl.TICKET_ASSIGNMENT_RULES, data);
  }

  static updateTicketAssignmentRule(id, data) {
    return this.apiput(ServerUrl.ticketAssignmentRule(id), data);
  }

  static deleteTicketAssignmentRule(id) {
    return this.apidelete(ServerUrl.ticketAssignmentRule(id));
  }

  static getSupportFormLink() {
    return this.apiget(ServerUrl.SUPPORT_FORM);
  }

  static resetSupportFormLink() {
    return this.apipost(ServerUrl.SUPPORT_FORM_RESET);
  }

  static getPublicSupportForm(token) {
    return this.apiget(ServerUrl.publicSupport(token));
  }

  static submitPublicSupportRequest(token, data) {
    return this.apipost(ServerUrl.publicSupport(token), data);
  }

  static getPublicFeedback(token) {
    return this.apiget(ServerUrl.publicFeedback(token));
  }

  static submitPublicFeedback(token, data) {
    return this.apipost(ServerUrl.publicFeedback(token), data);
  }

  // FR-7.1b/7.1c/7.4 campaign execution
  static launchCampaign(id) {
    return this.apipatch(ServerUrl.campaignLaunch(id));
  }

  static scheduleCampaign(id, scheduledAt) {
    return this.apipatch(ServerUrl.campaignSchedule(id), { scheduledAt });
  }

  static getCampaignMetrics(id) {
    return this.apiget(ServerUrl.campaignMetrics(id));
  }

  static getCampaignRecipients(id) {
    return this.apiget(ServerUrl.campaignRecipients(id));
  }

  // Module 8 sales force
  static getSalesTargets() {
    return this.apiget(ServerUrl.SALES_TARGETS);
  }

  static createSalesTarget(data) {
    return this.apipost(ServerUrl.SALES_TARGETS, data);
  }

  static updateSalesTarget(id, data) {
    return this.apiput(ServerUrl.salesTarget(id), data);
  }

  static deleteSalesTarget(id) {
    return this.apidelete(ServerUrl.salesTarget(id));
  }

  static logVisit(data) {
    return this.apipost(ServerUrl.SALES_VISITS, data);
  }

  static getVisits(params) {
    return this.apiget(ServerUrl.SALES_VISITS, params);
  }

  static getActivitySummary(params) {
    return this.apiget(ServerUrl.SALES_ACTIVITY_SUMMARY, params);
  }

  static getAnalyticsHeadline(params) {
    return this.apiget(ServerUrl.ANALYTICS_HEADLINE, params);
  }

  static getSalesReport(params) {
    return this.apiget(ServerUrl.ANALYTICS_SALES, params);
  }

  static getCustomerReport(params) {
    return this.apiget(ServerUrl.ANALYTICS_CUSTOMERS, params);
  }

  static getRevenueReport(params) {
    return this.apiget(ServerUrl.ANALYTICS_REVENUE, params);
  }

  static getTeamPerformance(params) {
    return this.apiget(ServerUrl.ANALYTICS_TEAM_PERFORMANCE, params);
  }

  static getReportFilterOptions() {
    return this.apiget(ServerUrl.ANALYTICS_FILTER_OPTIONS);
  }

  static getServiceReport(params) {
    return this.apiget(ServerUrl.ANALYTICS_SERVICE, params);
  }

  static getCampaignReport(params) {
    return this.apiget(ServerUrl.ANALYTICS_CAMPAIGNS, params);
  }

  static exportReport(report, params) {
    return this.axiosInstance.get(ServerUrl.analyticsExport(report), { params, responseType: 'blob' });
  }

  static getReportSchedules() {
    return this.apiget(ServerUrl.REPORT_SCHEDULES);
  }

  static createReportSchedule(data) {
    return this.apipost(ServerUrl.REPORT_SCHEDULES, data);
  }

  static updateReportSchedule(id, data) {
    return this.apiput(ServerUrl.reportSchedule(id), data);
  }

  static deleteReportSchedule(id) {
    return this.apidelete(ServerUrl.reportSchedule(id));
  }

  static sendReportSchedule(id) {
    return this.apipost(ServerUrl.reportScheduleSend(id));
  }

  static getDashboardPreferences() {
    return this.apiget(ServerUrl.DASHBOARD_PREFERENCES);
  }

  static saveDashboardPreferences(data) {
    return this.apiput(ServerUrl.DASHBOARD_PREFERENCES, data);
  }

  static getMyAttendance() {
    return this.apiget(ServerUrl.ATTENDANCE_ME);
  }

  static checkIn(location) {
    return this.apipost(ServerUrl.ATTENDANCE_CHECK_IN, location);
  }

  static checkOut(location) {
    return this.apipost(ServerUrl.ATTENDANCE_CHECK_OUT, location);
  }

  static getAttendance(params) {
    return this.apiget(ServerUrl.ATTENDANCE, params);
  }

  static getCommissions(params) {
    return this.apiget(ServerUrl.COMMISSIONS, params);
  }

  static approveCommission(id) {
    return this.apipatch(ServerUrl.commissionApprove(id));
  }

  static markCommissionPaid(id) {
    return this.apipatch(ServerUrl.commissionPaid(id));
  }

  static getCommissionRules() {
    return this.apiget(ServerUrl.COMMISSION_RULES);
  }

  static createCommissionRule(data) {
    return this.apipost(ServerUrl.COMMISSION_RULES, data);
  }

  static updateCommissionRule(id, data) {
    return this.apiput(ServerUrl.commissionRule(id), data);
  }

  static deleteCommissionRule(id) {
    return this.apidelete(ServerUrl.commissionRule(id));
  }


  // FR-7.2 segments
  static getSegments() {
    return this.apiget(ServerUrl.SEGMENTS);
  }

  static getSegmentCustomers(id) {
    return this.apiget(ServerUrl.segmentCustomers(id));
  }

  static createSegment(data) {
    return this.apipost(ServerUrl.SEGMENTS, data);
  }

  static updateSegment(id, data) {
    return this.apiput(ServerUrl.segment(id), data);
  }

  static deleteSegment(id) {
    return this.apidelete(ServerUrl.segment(id));
  }

  // FR-7.3 promotions
  static getPromotions() {
    return this.apiget(ServerUrl.PROMOTIONS);
  }

  static createPromotion(data) {
    return this.apipost(ServerUrl.PROMOTIONS, data);
  }

  static updatePromotion(id, data) {
    return this.apiput(ServerUrl.promotion(id), data);
  }

  static getPromotionRedemptions(id) {
    return this.apiget(ServerUrl.promotionRedemptions(id));
  }

  static redeemPromotion(id, data) {
    return this.apipost(ServerUrl.promotionRedemptions(id), data);
  }

  static getCustomerPromotions(customerId) {
    return this.apiget(ServerUrl.customerPromotions(customerId));
  }

  // FR-5.5 meetings
  static getMeetings(params) {
    return this.apiget(ServerUrl.MEETINGS, params);
  }

  static scheduleMeeting(data) {
    return this.apipost(ServerUrl.MEETINGS, data);
  }

  static updateMeeting(id, data) {
    return this.apiput(ServerUrl.meeting(id), data);
  }

  static cancelMeeting(id) {
    return this.apipatch(ServerUrl.meetingCancel(id));
  }

  static downloadMeetingIcs(id) {
    return this.axiosInstance.get(ServerUrl.meetingIcs(id), { responseType: 'blob' });
  }

  static getCalendarFeed() {
    return this.apiget(ServerUrl.MEETINGS_CALENDAR_FEED);
  }

  static resetCalendarFeed() {
    return this.apipost(ServerUrl.MEETINGS_CALENDAR_FEED_RESET);
  }

  static getCommunications(params) {
    return this.apiget(ServerUrl.COMMUNICATIONS, params);
  }

  static logCall(data) {
    return this.apipost(ServerUrl.COMMUNICATION_CALLS, data);
  }

  static sendCommunicationEmail(data) {
    return this.apipost(ServerUrl.COMMUNICATION_EMAIL, data);
  }

  static sendWhatsApp(data) {
    return this.apipost(ServerUrl.COMMUNICATION_WHATSAPP, data);
  }

  static getSmsAlerts() {
    return this.apiget(ServerUrl.COMMUNICATION_SMS_ALERTS);
  }

  static updateSmsAlerts(rules) {
    return this.apiput(ServerUrl.COMMUNICATION_SMS_ALERTS, rules);
  }

  static getIndustries(includeInactive = false) {
    return this.apiget(ServerUrl.INDUSTRIES, { includeInactive });
  }

  static createIndustry(data) {
    return this.apipost(ServerUrl.INDUSTRIES, data);
  }

  static updateIndustry(code, data) {
    return this.apiput(ServerUrl.industry(code), data);
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

  static assignLeadTeam(id, data) {
    return this.apipatch(ServerUrl.leadTeam(id), data);
  }

  static getLeadAssignableTeams() {
    return this.apiget(ServerUrl.LEAD_ASSIGNABLE_TEAMS);
  }

  // ------------------ Opportunity APIs ------------------
  static getOpportunities(params) {
    return this.apiget(ServerUrl.OPPORTUNITIES, params);
  }

  static getOpportunitySummary() {
    return this.apiget(ServerUrl.OPPORTUNITIES_SUMMARY);
  }

  static getPipelineStages(includeInactive = false) {
    return this.apiget(ServerUrl.OPPORTUNITY_STAGES, { includeInactive });
  }

  static createPipelineStage(data) {
    return this.apipost(ServerUrl.OPPORTUNITY_STAGES, data);
  }

  static updatePipelineStage(code, data) {
    return this.apiput(ServerUrl.pipelineStage(code), data);
  }

  static reorderPipelineStages(codes) {
    return this.apiput(ServerUrl.OPPORTUNITY_STAGES_ORDER, codes);
  }

  static getSalesPerformance() {
    return this.apiget(ServerUrl.OPPORTUNITIES_PERFORMANCE);
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

  static getPublicQuotation(token) {
    return this.apiget(ServerUrl.publicQuotation(token));
  }

  static downloadPublicQuotationPdf(token) {
    return this.axiosInstance.get(ServerUrl.publicQuotationPdf(token), { responseType: "blob" });
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

  static claimTicket(id) {
    return this.apipost(ServerUrl.ticketClaim(id));
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

  static getTenantAdminDashboard() {
    return this.apiget(ServerUrl.TENANT_ADMIN_DASHBOARD);
  }

  static createPaymentOrder(data) {
    return this.apipost(ServerUrl.PAYMENT_CREATE_ORDER, data);
  }

  static verifyPaymentOrder(data) {
    return this.apipost(ServerUrl.PAYMENT_VERIFY_ORDER, data);
  }

  static getCurrentSubscription() {
    return this.apiget(ServerUrl.SUBSCRIPTION_CURRENT);
  }

  static getSubscriptionPlans() {
    return this.apiget(ServerUrl.SUBSCRIPTION_PLANS);
  }

  static upgradeSubscription(data) {
    return this.apipost(ServerUrl.SUBSCRIPTION_UPGRADE, data);
  }

  // ------------------ Sales Team APIs (tenant admin) ------------------
  static getLeadAssignmentRules() {
    return this.apiget(ServerUrl.LEAD_ASSIGNMENT_RULES);
  }

  static createLeadAssignmentRule(data) {
    return this.apipost(ServerUrl.LEAD_ASSIGNMENT_RULES, data);
  }

  static updateLeadAssignmentRule(id, data) {
    return this.apiput(ServerUrl.leadAssignmentRule(id), data);
  }

  static deleteLeadAssignmentRule(id) {
    return this.apidelete(ServerUrl.leadAssignmentRule(id));
  }

  static getSalesTeams() {
    return this.apiget(ServerUrl.SALES_TEAMS);
  }

  static createSalesTeam(data) {
    return this.apipost(ServerUrl.SALES_TEAMS, data);
  }

  static updateSalesTeam(id, data) {
    return this.apiput(ServerUrl.salesTeam(id), data);
  }

  static setSalesTeamMembers(id, data) {
    return this.apiput(ServerUrl.salesTeamMembers(id), data);
  }

  static deleteSalesTeam(id) {
    return this.apidelete(ServerUrl.salesTeam(id));
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