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
    static AUTH_RESEND_VERIFICATION = ServerUrl.API_MODULE_AUTH + "/resend-verification";

  static API_MODULE_USER = "/api";

    static USERS = ServerUrl.API_MODULE_USER + "/users";
    static TENANTS = ServerUrl.API_MODULE_USER + "/tenants";

    // customers
    static CUSTOMERS = ServerUrl.API_MODULE_USER + "/customers";
    static customer = (id) => `${ServerUrl.CUSTOMERS}/${id}`;
    static customerArchive = (id) => `${ServerUrl.CUSTOMERS}/${id}/archive`;
    static customerRestore = (id) => `${ServerUrl.CUSTOMERS}/${id}/restore`;
    static customerContacts = (customerId) => `${ServerUrl.CUSTOMERS}/${customerId}/contacts`;
    static customerPurchases = (customerId) => `${ServerUrl.CUSTOMERS}/${customerId}/purchases`;
    static customerDocuments = (customerId) => `${ServerUrl.CUSTOMERS}/${customerId}/documents`;
    static customerDocument = (customerId, documentId) => `${ServerUrl.CUSTOMERS}/${customerId}/documents/${documentId}`;

    // company branding (AC-4.2)
    static BRANDING = ServerUrl.API_MODULE_USER + "/branding";
    static BRANDING_LOGO = ServerUrl.BRANDING + "/logo";

    // industries (FR-1.6)
    static INDUSTRIES = ServerUrl.API_MODULE_USER + "/industries";
    static industry = (code) => `${ServerUrl.INDUSTRIES}/${code}`;

    // communications (Module 5)
    // service support (FR-6.1 / 6.3b / 6.5)
    static customerAssets = (customerId) => `${ServerUrl.CUSTOMERS}/${customerId}/assets`;
    static customerAsset = (customerId, assetId) => `${ServerUrl.CUSTOMERS}/${customerId}/assets/${assetId}`;
    static TICKET_ASSIGNMENT_RULES = ServerUrl.API_MODULE_USER + "/ticket-assignment-rules";
    static ticketAssignmentRule = (id) => `${ServerUrl.TICKET_ASSIGNMENT_RULES}/${id}`;
    static SUPPORT_FORM = ServerUrl.API_MODULE_USER + "/support-form";
    static SUPPORT_FORM_RESET = ServerUrl.SUPPORT_FORM + "/reset";
    static publicSupport = (token) => `${ServerUrl.API_MODULE_USER}/public/support/${encodeURIComponent(token)}`;
    static publicFeedback = (token) => `${ServerUrl.API_MODULE_USER}/public/feedback/${encodeURIComponent(token)}`;

    // meetings (FR-5.5)
    static MEETINGS = ServerUrl.API_MODULE_USER + "/meetings";
    static meeting = (id) => `${ServerUrl.MEETINGS}/${id}`;
    static meetingCancel = (id) => `${ServerUrl.MEETINGS}/${id}/cancel`;
    static meetingIcs = (id) => `${ServerUrl.MEETINGS}/${id}/ics`;
    static MEETINGS_CALENDAR_FEED = ServerUrl.MEETINGS + "/calendar-feed";
    static MEETINGS_CALENDAR_FEED_RESET = ServerUrl.MEETINGS + "/calendar-feed/reset";

    static COMMUNICATIONS = ServerUrl.API_MODULE_USER + "/communications";
    static COMMUNICATION_CALLS = ServerUrl.COMMUNICATIONS + "/calls";
    static COMMUNICATION_EMAIL = ServerUrl.COMMUNICATIONS + "/email";
    static COMMUNICATION_WHATSAPP = ServerUrl.COMMUNICATIONS + "/whatsapp";
    static COMMUNICATION_SMS_ALERTS = ServerUrl.COMMUNICATIONS + "/sms-alerts";
    static customerContact = (customerId, contactId) =>
      `${ServerUrl.CUSTOMERS}/${customerId}/contacts/${contactId}`;

    // leads
    static LEADS = ServerUrl.API_MODULE_USER + "/leads";
    static lead = (id) => `${ServerUrl.LEADS}/${id}`;
    static leadStage = (id) => `${ServerUrl.LEADS}/${id}/stage`;
    static leadAssign = (id) => `${ServerUrl.LEADS}/${id}/assign`;
    static leadTeam = (id) => `${ServerUrl.LEADS}/${id}/team`;
    static LEAD_ASSIGNABLE_TEAMS = ServerUrl.LEADS + "/assignable-teams";
    static LEAD_ASSIGNMENT_RULES = ServerUrl.API_MODULE_USER + "/lead-assignment-rules";
    static leadAssignmentRule = (id) => `${ServerUrl.LEAD_ASSIGNMENT_RULES}/${id}`;

    // opportunities
    static OPPORTUNITIES = ServerUrl.API_MODULE_USER + "/opportunities";
    static OPPORTUNITIES_SUMMARY = ServerUrl.OPPORTUNITIES + "/summary";
    static OPPORTUNITIES_PERFORMANCE = ServerUrl.OPPORTUNITIES + "/performance";
    // FR-3.2: configurable sales stages
    static OPPORTUNITY_STAGES = ServerUrl.API_MODULE_USER + "/opportunity-stages";
    static OPPORTUNITY_STAGES_ORDER = ServerUrl.OPPORTUNITY_STAGES + "/order";
    static pipelineStage = (code) => `${ServerUrl.OPPORTUNITY_STAGES}/${encodeURIComponent(code)}`;
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
    // FR-4.5: customer view link (public)
    static publicQuotation = (token) => `${ServerUrl.API_MODULE_USER}/public/quotations/${encodeURIComponent(token)}`;
    static publicQuotationPdf = (token) => `${ServerUrl.publicQuotation(token)}/pdf`;

    // products (quotation price list)
    static PRODUCTS = ServerUrl.API_MODULE_USER + "/products";
    static product = (id) => `${ServerUrl.PRODUCTS}/${id}`;
    static productDeactivate = (id) => `${ServerUrl.PRODUCTS}/${id}/deactivate`;
    static productActivate = (id) => `${ServerUrl.PRODUCTS}/${id}/activate`;

    // service tickets
    static TICKETS = ServerUrl.API_MODULE_USER + "/tickets";
    static TICKETS_SUMMARY = ServerUrl.TICKETS + "/summary";
    static ticket = (id) => `${ServerUrl.TICKETS}/${id}`;
    static ticketAssign = (id) => `${ServerUrl.TICKETS}/${id}/assign`;
    static ticketStatus = (id) => `${ServerUrl.TICKETS}/${id}/status`;
    static ticketFeedback = (id) => `${ServerUrl.TICKETS}/${id}/feedback`;
    static ticketClaim = (id) => `${ServerUrl.TICKETS}/${id}/claim`;

    // campaigns
    static CAMPAIGNS = ServerUrl.API_MODULE_USER + "/campaigns";
    static CAMPAIGNS_SUMMARY = ServerUrl.CAMPAIGNS + "/summary";
    static campaign = (id) => `${ServerUrl.CAMPAIGNS}/${id}`;
    static campaignStatus = (id) => `${ServerUrl.CAMPAIGNS}/${id}/status`;
    static campaignLeads = (id) => `${ServerUrl.CAMPAIGNS}/${id}/leads`;
    // FR-7.1b/7.1c/7.4
    static campaignLaunch = (id) => `${ServerUrl.CAMPAIGNS}/${id}/launch`;
    static campaignSchedule = (id) => `${ServerUrl.CAMPAIGNS}/${id}/schedule`;
    static campaignMetrics = (id) => `${ServerUrl.CAMPAIGNS}/${id}/metrics`;
    static campaignRecipients = (id) => `${ServerUrl.CAMPAIGNS}/${id}/recipients`;
    // FR-7.2 segments, FR-7.3 promotions
    static SEGMENTS = ServerUrl.API_MODULE_USER + "/segments";
    static segment = (id) => `${ServerUrl.SEGMENTS}/${id}`;
    static segmentCustomers = (id) => `${ServerUrl.SEGMENTS}/${id}/customers`;
    static PROMOTIONS = ServerUrl.API_MODULE_USER + "/promotions";
    static promotion = (id) => `${ServerUrl.PROMOTIONS}/${id}`;
    static promotionRedemptions = (id) => `${ServerUrl.PROMOTIONS}/${id}/redemptions`;
    static customerPromotions = (customerId) => `${ServerUrl.API_MODULE_USER}/customers/${customerId}/promotions`;

    static ADMIN_TENANT = ServerUrl.API_MODULE_USER + "/super-admin/tenants";
    static ADMIN_DASHBOARD = ServerUrl.API_MODULE_USER + "/dashboard/super-admin";
    static TENANT_ADMIN_DASHBOARD = ServerUrl.API_MODULE_USER + "/dashboard/tenant-admin-status";

    // plans
    static PLANS = ServerUrl.API_MODULE_USER + "/plans";

    // payments
    static PAYMENTS = ServerUrl.API_MODULE_USER + "/payments";
    static PAYMENT_CREATE_ORDER = ServerUrl.PAYMENTS + "/create-order";
    static PAYMENT_VERIFY_ORDER = ServerUrl.PAYMENTS + "/verify";

    // subscriptions
    static SUBSCRIPTIONS = ServerUrl.API_MODULE_USER + "/subscriptions";
    static SUBSCRIPTION_CURRENT = ServerUrl.SUBSCRIPTIONS + "/current";
    static SUBSCRIPTION_PLANS = ServerUrl.SUBSCRIPTIONS + "/plans";
    static SUBSCRIPTION_UPGRADE = ServerUrl.SUBSCRIPTIONS + "/upgrade";

    // sales teams (tenant admin)
    static SALES_TEAMS = ServerUrl.API_MODULE_USER + "/sales-teams";
    static salesTeam = (id) => `${ServerUrl.SALES_TEAMS}/${id}`;
    static salesTeamMembers = (id) => `${ServerUrl.SALES_TEAMS}/${id}/members`;

  static PLAN_PRICES = ServerUrl.PLANS + "/prices"; // GET list
  static planPrice = (plan) => `${ServerUrl.PLANS}/${plan}/price`; // PUT single
}

export default ServerUrl;