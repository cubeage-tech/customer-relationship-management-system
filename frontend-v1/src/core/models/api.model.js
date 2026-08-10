/**
 * @typedef {Object} ApiResponse
 * @property {boolean} success
 * @property {string} [message]
 * @property {*} [data]
 */

/**
 * Internal CRM role names. Must stay in sync with USER_ROLES in
 * core/constants/app.constant.js.
 *
 * @typedef {'super_admin'
 *   | 'sales_manager'
 *   | 'sales_executive'
 *   | 'marketing_executive'
 *   | 'service_agent'
 *   | 'finance_approver'
 *   | 'executive_owner'} UserRole
 */

/**
 * @typedef {Object} AuthUser
 * @property {string} id
 * @property {string} name
 * @property {string} email
 * @property {UserRole} role
 * @property {string} [tenantId]
 * @property {string} [teamId]
 */

/**
 * @typedef {Object} Customer
 * @property {string} id
 * @property {string} name
 * @property {string} [organization]
 * @property {string} [email]
 * @property {string} [phone]
 * @property {string} [ownerId]
 */

/**
 * @typedef {Object} Lead
 * @property {string} id
 * @property {string} name
 * @property {string} [source]
 * @property {string} [status]
 * @property {string} [ownerId]
 */

/**
 * @typedef {Object} Opportunity
 * @property {string} id
 * @property {string} title
 * @property {string} customerId
 * @property {number} [value]
 * @property {string} [stage]
 * @property {string} [ownerId]
 */

/**
 * @typedef {Object} ServiceTicket
 * @property {string} id
 * @property {string} subject
 * @property {string} customerId
 * @property {string} [priority]
 * @property {string} [status]
 * @property {string} [assigneeId]
 */
