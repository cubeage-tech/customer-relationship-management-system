import { USER_ROLES } from '../constants/app.constant';

/**
 * Seed accounts for local development only, one per CRM role.
 * Consumed exclusively by auth.service.js's mock login path, which is
 * compiled out of production builds — see the guard there for why this
 * file can never be reached outside `vite dev`.
 */
export const DEV_USERS = [
  {
    id: 'dev-super-admin',
    role: USER_ROLES.SUPER_ADMIN,
    name: 'Ava Administrator',
    email: 'admin@crm.dev',
  },
  {
    id: 'dev-sales-manager',
    role: USER_ROLES.SALES_MANAGER,
    name: 'Sam Manager',
    email: 'sales.manager@crm.dev',
  },
  {
    id: 'dev-sales-executive',
    role: USER_ROLES.SALES_EXECUTIVE,
    name: 'Eli Executive',
    email: 'sales.exec@crm.dev',
  },
  {
    id: 'dev-marketing-executive',
    role: USER_ROLES.MARKETING_EXECUTIVE,
    name: 'Mia Marketer',
    email: 'marketing@crm.dev',
  },
  {
    id: 'dev-service-agent',
    role: USER_ROLES.SERVICE_AGENT,
    name: 'Theo Agent',
    email: 'service@crm.dev',
  },
  {
    id: 'dev-finance-approver',
    role: USER_ROLES.FINANCE_APPROVER,
    name: 'Farah Finance',
    email: 'finance@crm.dev',
  },
  {
    id: 'dev-executive-owner',
    role: USER_ROLES.EXECUTIVE_OWNER,
    name: 'Owen Owner',
    email: 'owner@crm.dev',
  },
];
