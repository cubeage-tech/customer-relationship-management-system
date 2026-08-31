export const APPLICATION_CONSTANTS = {
  STORAGE: {
    TOKEN: "token",
    USER_DETAILS: "user",
    LANGUAGE: "lang",
    ROUTE_STEP_1: "routeStep1",
    ROUTE_STEP_2: "routeStep2",
  },
  // FIX: Only image types are accepted by the backend (JPEG, PNG, WebP, HEIC).
  // PDF and XLS were listed here but the backend rejects them — removed to
  // prevent confusing UX where users select a file that then gets rejected.
  ALLOW_FILES_EXTENSION: "image/jpeg,image/png,image/webp,image/heic",
  CONTENT_TYPES: "application/json",
};

// Customer Relationship Management (CRM) — application-wide constants

export const APP_NAME = 'Customer Relationship Management';

export const APP_SHORT_NAME = 'CRM';

export const USER_ROLES = {
  SUPER_ADMIN: 'super_admin',
  ADMIN: 'admin',
  SALES_MANAGER: 'sales_manager',
  SALES_EXECUTIVE: 'sales_executive',
  MARKETING_EXECUTIVE: 'marketing_executive',
  SERVICE_AGENT: 'service_agent',
  FINANCE_APPROVER: 'finance_approver',
  EXECUTIVE_OWNER: 'executive_owner',
};

/** Human readable name for every internal role name. */
export const ROLE_LABELS = {
  [USER_ROLES.SUPER_ADMIN]: 'Platform Super Admin',
  [USER_ROLES.ADMIN]: 'Tenant Administrator',
  [USER_ROLES.SALES_MANAGER]: 'Sales Manager',
  [USER_ROLES.SALES_EXECUTIVE]: 'Sales Executive',
  [USER_ROLES.MARKETING_EXECUTIVE]: 'Marketing Executive',
  [USER_ROLES.SERVICE_AGENT]: 'Service Agent',
  [USER_ROLES.FINANCE_APPROVER]: 'Finance/Approver',
  [USER_ROLES.EXECUTIVE_OWNER]: 'Executive/Owner',
};

export const ROLE_DESCRIPTIONS = {
  [USER_ROLES.SUPER_ADMIN]:
    'Devniks/Cubeage platform staff. Manages every tenant on SmartCRM AI and sees cross-tenant sales and product analytics.',
  [USER_ROLES.ADMIN]:
    'Tenant owner. Full access to every module, all teams and tenant administration settings, including adding team members.',
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

/**
 * Roles selectable during public self-registration. Signing up only ever creates a new
 * tenant + its admin (owner) — every other CRM role is added by that admin from the Users
 * page, never self-registered, so tenants can't be joined just by knowing their name.
 */
export const SIGNUP_ROLES = [USER_ROLES.ADMIN];

/** Roles a tenant admin may hand out to team members from the Users page. */
export const TEAM_ROLES = [
  USER_ROLES.SALES_MANAGER,
  USER_ROLES.SALES_EXECUTIVE,
  USER_ROLES.MARKETING_EXECUTIVE,
  USER_ROLES.SERVICE_AGENT,
  USER_ROLES.FINANCE_APPROVER,
  USER_ROLES.EXECUTIVE_OWNER,
];

export const isValidRole = (role) => Object.values(USER_ROLES).includes(role);

export const STORAGE_KEYS = {
  AUTH_TOKEN: 'crm_auth_token',
  AUTH_USER: 'crm_auth_user',
};

// Customer industry classification — mirrors backend IndustryType db values (FR-1.6).
export const INDUSTRY_TYPES = {
  MANUFACTURING: 'manufacturing',
  ENGINEERING: 'engineering',
  REAL_ESTATE: 'real_estate',
  CONSTRUCTION: 'construction',
  AUTOMOBILE: 'automobile',
  INDUSTRIAL_EQUIPMENT: 'industrial_equipment',
  SOFTWARE: 'software',
  TRADING: 'trading',
  SERVICES: 'services',
  HEALTHCARE: 'healthcare',
  OTHER: 'other',
};

export const INDUSTRY_LABELS = {
  [INDUSTRY_TYPES.MANUFACTURING]: 'Manufacturing',
  [INDUSTRY_TYPES.ENGINEERING]: 'Engineering',
  [INDUSTRY_TYPES.REAL_ESTATE]: 'Real Estate',
  [INDUSTRY_TYPES.CONSTRUCTION]: 'Construction',
  [INDUSTRY_TYPES.AUTOMOBILE]: 'Automobile',
  [INDUSTRY_TYPES.INDUSTRIAL_EQUIPMENT]: 'Industrial Equipment',
  [INDUSTRY_TYPES.SOFTWARE]: 'Software',
  [INDUSTRY_TYPES.TRADING]: 'Trading',
  [INDUSTRY_TYPES.SERVICES]: 'Services',
  [INDUSTRY_TYPES.HEALTHCARE]: 'Healthcare',
  [INDUSTRY_TYPES.OTHER]: 'Other',
};

export const INDUSTRY_OPTIONS = Object.values(INDUSTRY_TYPES).map((value) => ({
  value,
  label: INDUSTRY_LABELS[value],
}));

export const CUSTOMER_STATUS = {
  ACTIVE: 'active',
  ARCHIVED: 'archived',
};

// Lead source — mirrors backend LeadSource db values (FR-2.2).
export const LEAD_SOURCES = {
  WEBSITE: 'website',
  REFERRAL: 'referral',
  EXHIBITION: 'exhibition',
  COLD_CALL: 'cold_call',
  MARKETING_CAMPAIGN: 'marketing_campaign',
  OTHER: 'other',
};

export const LEAD_SOURCE_LABELS = {
  [LEAD_SOURCES.WEBSITE]: 'Website',
  [LEAD_SOURCES.REFERRAL]: 'Referral',
  [LEAD_SOURCES.EXHIBITION]: 'Exhibition',
  [LEAD_SOURCES.COLD_CALL]: 'Cold call',
  [LEAD_SOURCES.MARKETING_CAMPAIGN]: 'Marketing campaign',
  [LEAD_SOURCES.OTHER]: 'Other',
};

export const LEAD_SOURCE_OPTIONS = Object.values(LEAD_SOURCES).map((value) => ({
  value,
  label: LEAD_SOURCE_LABELS[value],
}));

// Lead pipeline stage — mirrors backend LeadStage db values / declaration order (§6.2).
export const LEAD_STAGES = {
  NEW_LEAD: 'new_lead',
  CONTACTED: 'contacted',
  MEETING: 'meeting',
  QUOTATION: 'quotation',
  NEGOTIATION: 'negotiation',
  CONVERTED: 'converted',
};

export const LEAD_STAGE_LABELS = {
  [LEAD_STAGES.NEW_LEAD]: 'New Lead',
  [LEAD_STAGES.CONTACTED]: 'Contacted',
  [LEAD_STAGES.MEETING]: 'Meeting',
  [LEAD_STAGES.QUOTATION]: 'Quotation',
  [LEAD_STAGES.NEGOTIATION]: 'Negotiation',
  [LEAD_STAGES.CONVERTED]: 'Converted',
};

/** Ordered pipeline — index doubles as the stage's position for skip detection. */
export const LEAD_STAGE_PIPELINE = Object.values(LEAD_STAGES);

export const LEAD_STAGE_OPTIONS = LEAD_STAGE_PIPELINE.map((value) => ({
  value,
  label: LEAD_STAGE_LABELS[value],
}));

// Sales opportunity stage — mirrors backend OpportunityStage db values (FR-3.2).
export const OPPORTUNITY_STAGES = {
  QUALIFICATION: 'qualification',
  PROPOSAL: 'proposal',
  NEGOTIATION: 'negotiation',
  WON: 'won',
  LOST: 'lost',
};

export const OPPORTUNITY_STAGE_LABELS = {
  [OPPORTUNITY_STAGES.QUALIFICATION]: 'Qualification',
  [OPPORTUNITY_STAGES.PROPOSAL]: 'Proposal',
  [OPPORTUNITY_STAGES.NEGOTIATION]: 'Negotiation',
  [OPPORTUNITY_STAGES.WON]: 'Won',
  [OPPORTUNITY_STAGES.LOST]: 'Lost',
};

export const OPPORTUNITY_STAGE_OPTIONS = Object.values(OPPORTUNITY_STAGES).map((value) => ({
  value,
  label: OPPORTUNITY_STAGE_LABELS[value],
}));

// Quotation customer-response status — mirrors backend QuotationStatus db values (FR-4.5).
export const QUOTATION_STATUSES = {
  DRAFT: 'draft',
  PENDING: 'pending',
  VIEWED: 'viewed',
  APPROVED: 'approved',
  REJECTED: 'rejected',
  EXPIRED: 'expired',
};

export const QUOTATION_STATUS_LABELS = {
  [QUOTATION_STATUSES.DRAFT]: 'Draft',
  [QUOTATION_STATUSES.PENDING]: 'Pending',
  [QUOTATION_STATUSES.VIEWED]: 'Viewed',
  [QUOTATION_STATUSES.APPROVED]: 'Approved',
  [QUOTATION_STATUSES.REJECTED]: 'Rejected',
  [QUOTATION_STATUSES.EXPIRED]: 'Expired',
};

/** Statuses that can be recorded on the customer's behalf once a quotation has been sent. */
export const QUOTATION_CUSTOMER_STATUS_OPTIONS = [
  QUOTATION_STATUSES.VIEWED,
  QUOTATION_STATUSES.APPROVED,
  QUOTATION_STATUSES.REJECTED,
  QUOTATION_STATUSES.EXPIRED,
].map((value) => ({ value, label: QUOTATION_STATUS_LABELS[value] }));

// Discount approval status — mirrors backend DiscountApprovalStatus db values (BR-3/FR-4.3).
export const DISCOUNT_APPROVAL_STATUS_LABELS = {
  not_required: 'Not required',
  pending: 'Awaiting approval',
  approved: 'Approved',
  rejected: 'Rejected',
};

// Service ticket priority — mirrors backend TicketPriority db values (FR-6.1).
export const TICKET_PRIORITIES = {
  CRITICAL: 'critical',
  HIGH: 'high',
  MEDIUM: 'medium',
  LOW: 'low',
};

export const TICKET_PRIORITY_LABELS = {
  [TICKET_PRIORITIES.CRITICAL]: 'Critical',
  [TICKET_PRIORITIES.HIGH]: 'High',
  [TICKET_PRIORITIES.MEDIUM]: 'Medium',
  [TICKET_PRIORITIES.LOW]: 'Low',
};

export const TICKET_PRIORITY_OPTIONS = Object.values(TICKET_PRIORITIES).map((value) => ({
  value,
  label: TICKET_PRIORITY_LABELS[value],
}));

// Service ticket status — mirrors backend TicketStatus db values (FR-6.2).
export const TICKET_STATUSES = {
  OPEN: 'open',
  ASSIGNED: 'assigned',
  IN_PROGRESS: 'in_progress',
  RESOLVED: 'resolved',
  CLOSED: 'closed',
};

export const TICKET_STATUS_LABELS = {
  [TICKET_STATUSES.OPEN]: 'Open',
  [TICKET_STATUSES.ASSIGNED]: 'Assigned',
  [TICKET_STATUSES.IN_PROGRESS]: 'In Progress',
  [TICKET_STATUSES.RESOLVED]: 'Resolved',
  [TICKET_STATUSES.CLOSED]: 'Closed',
};

export const TICKET_STATUS_OPTIONS = Object.values(TICKET_STATUSES).map((value) => ({
  value,
  label: TICKET_STATUS_LABELS[value],
}));

// SLA status — derived by the backend (FR-6.4), never persisted.
export const TICKET_SLA_STATUS_LABELS = {
  on_track: 'On track',
  at_risk: 'At risk',
  breached: 'Breached',
  met: 'Met',
};

// Campaign channel — mirrors backend CampaignChannel db values (SRS §6.7).
export const CAMPAIGN_CHANNELS = {
  EMAIL: 'email',
  SOCIAL_MEDIA: 'social_media',
  SMS: 'sms',
  EVENT: 'event',
  WEBINAR: 'webinar',
  PAID_ADS: 'paid_ads',
  OTHER: 'other',
};

export const CAMPAIGN_CHANNEL_LABELS = {
  [CAMPAIGN_CHANNELS.EMAIL]: 'Email',
  [CAMPAIGN_CHANNELS.SOCIAL_MEDIA]: 'Social Media',
  [CAMPAIGN_CHANNELS.SMS]: 'SMS',
  [CAMPAIGN_CHANNELS.EVENT]: 'Event',
  [CAMPAIGN_CHANNELS.WEBINAR]: 'Webinar',
  [CAMPAIGN_CHANNELS.PAID_ADS]: 'Paid Ads',
  [CAMPAIGN_CHANNELS.OTHER]: 'Other',
};

export const CAMPAIGN_CHANNEL_OPTIONS = Object.values(CAMPAIGN_CHANNELS).map((value) => ({
  value,
  label: CAMPAIGN_CHANNEL_LABELS[value],
}));

// Campaign status — mirrors backend CampaignStatus db values (SRS §6.7).
export const CAMPAIGN_STATUSES = {
  DRAFT: 'draft',
  ACTIVE: 'active',
  PAUSED: 'paused',
  COMPLETED: 'completed',
  CANCELLED: 'cancelled',
};

export const CAMPAIGN_STATUS_LABELS = {
  [CAMPAIGN_STATUSES.DRAFT]: 'Draft',
  [CAMPAIGN_STATUSES.ACTIVE]: 'Active',
  [CAMPAIGN_STATUSES.PAUSED]: 'Paused',
  [CAMPAIGN_STATUSES.COMPLETED]: 'Completed',
  [CAMPAIGN_STATUSES.CANCELLED]: 'Cancelled',
};

export const CAMPAIGN_STATUS_OPTIONS = Object.values(CAMPAIGN_STATUSES).map((value) => ({
  value,
  label: CAMPAIGN_STATUS_LABELS[value],
}));
