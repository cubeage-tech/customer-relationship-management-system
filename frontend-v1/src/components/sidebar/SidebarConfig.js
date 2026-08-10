import {
  LayoutDashboard,
  Users,
  UserPlus,
  Target,
  FileText,
  LifeBuoy,
  Megaphone,
  BadgeCheck,
  BarChart3,
  Settings,
  ShieldCheck,
} from "lucide-react";
import RoutePath, { getRoleHomeRoute } from "../../core/constants/routes.constant";
import { PERMISSIONS } from "../../core/constants/permission.constant";
import { hasAnyPermission } from "../../core/utils/permission";

/**
 * Single source of truth for the CRM sidebar.
 * Every item declares the permissions that make it visible, so navigation can
 * never drift away from the RBAC matrix in permission.constant.js.
 */
export const SIDEBAR_ITEMS = [
  {
    label: "Dashboard",
    icon: LayoutDashboard,
    // Resolved per user — each role lands on its own dashboard.
    path: null,
    permissions: null,
  },
  {
    label: "Customers",
    icon: Users,
    path: RoutePath.CUSTOMERS,
    permissions: [PERMISSIONS.CUSTOMERS_VIEW],
  },
  {
    label: "Leads",
    icon: UserPlus,
    path: RoutePath.LEADS,
    permissions: [PERMISSIONS.LEADS_VIEW],
  },
  {
    label: "Opportunities",
    icon: Target,
    path: RoutePath.OPPORTUNITIES,
    permissions: [PERMISSIONS.OPPORTUNITIES_VIEW],
  },
  {
    label: "Quotations",
    icon: FileText,
    path: RoutePath.QUOTATIONS,
    permissions: [PERMISSIONS.QUOTATIONS_VIEW],
  },
  {
    label: "Service Tickets",
    icon: LifeBuoy,
    path: RoutePath.SERVICE_TICKETS,
    permissions: [PERMISSIONS.TICKETS_VIEW, PERMISSIONS.TICKETS_CREATE],
  },
  {
    label: "Campaigns",
    icon: Megaphone,
    path: RoutePath.CAMPAIGNS,
    permissions: [PERMISSIONS.CAMPAIGNS_VIEW],
  },
  {
    label: "Approvals",
    icon: BadgeCheck,
    path: RoutePath.APPROVALS,
    permissions: [
      PERMISSIONS.QUOTATIONS_APPROVE,
      PERMISSIONS.QUOTATIONS_APPROVE_DISCOUNT,
    ],
  },
  {
    label: "Reports",
    icon: BarChart3,
    path: RoutePath.REPORTS,
    permissions: [PERMISSIONS.REPORTS_VIEW],
  },
  {
    label: "Users",
    icon: ShieldCheck,
    path: RoutePath.ADMIN_USERS,
    permissions: [PERMISSIONS.USERS_VIEW],
  },
  {
    label: "Settings",
    icon: Settings,
    path: RoutePath.ADMIN_SETTINGS,
    permissions: [PERMISSIONS.SETTINGS_VIEW],
  },
];

/** Menu items the given CRM user is allowed to see. */
export const getSidebarMenu = (user) => {
  if (!user?.role) return [];

  return SIDEBAR_ITEMS.filter(
    (item) => !item.permissions || hasAnyPermission(user, item.permissions)
  ).map((item) => ({
    ...item,
    path: item.path || getRoleHomeRoute(user.role),
  }));
};
