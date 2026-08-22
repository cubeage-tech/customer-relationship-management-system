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
  Building2,
  CreditCard,
  ScrollText,
} from "lucide-react";
import RoutePath, { getRoleHomeRoute } from "../../core/constants/routes.constant";
import { USER_ROLES } from "../../core/constants/app.constant";
import { PERMISSIONS } from "../../core/constants/permission.constant";
import { hasAnyPermission } from "../../core/utils/permission";

/**
 * Single source of truth for the tenant CRM sidebar (every non-super_admin role).
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

/** Platform-level sidebar, grouped into sections — super_admin only. */
export const SUPER_ADMIN_SIDEBAR_GROUPS = [
  {
    section: "PLATFORM",
    items: [
      {
        label: "Dashboard",
        icon: LayoutDashboard,
        path: RoutePath.SUPER_ADMIN_DASHBOARD,
        permissions: null,
      },
      {
        label: "Tenants / Clients",
        icon: Building2,
        path: RoutePath.PLATFORM_TENANTS,
        permissions: [PERMISSIONS.TENANTS_VIEW],
      },
      {
        label: "Subscription Plans",
        icon: CreditCard,
        path: RoutePath.PLATFORM_SUBSCRIPTION_PLANS,
        permissions: [PERMISSIONS.SUBSCRIPTION_PLANS_VIEW],
      },
      {
        label: "Role & Permission",
        icon: ShieldCheck,
        path: RoutePath.PLATFORM_ROLES_PERMISSIONS,
        permissions: [PERMISSIONS.PLATFORM_ROLES_VIEW],
      },
      {
        label: "System Settings",
        icon: Settings,
        path: RoutePath.PLATFORM_SETTINGS,
        permissions: [PERMISSIONS.PLATFORM_SETTINGS_VIEW],
      },
      {
        label: "Reports",
        icon: BarChart3,
        path: RoutePath.PLATFORM_REPORTS,
        permissions: [PERMISSIONS.PLATFORM_REPORTS_VIEW],
      },
      {
        label: "Audit Logs",
        icon: ScrollText,
        path: RoutePath.PLATFORM_AUDIT_LOGS,
        permissions: [PERMISSIONS.AUDIT_LOGS_VIEW],
      },
    ],
  },
  {
    section: "SUPPORT",
    items: [
      {
        label: "Help Center",
        icon: LifeBuoy,
        path: RoutePath.HELP_CENTER,
        permissions: null,
      },
    ],
  },
];

/**
 * Menu groups the given CRM user is allowed to see, shaped as
 * `[{ section, items: [{ path, label, icon, badge?, isNew? }] }]` so the
 * sidebar can render section headers uniformly for every role.
 */
export const getSidebarMenu = (user) => {
  if (!user?.role) return [];

  if (user.role === USER_ROLES.SUPER_ADMIN) {
    return SUPER_ADMIN_SIDEBAR_GROUPS.map((group) => ({
      ...group,
      items: group.items.filter(
        (item) => !item.permissions || hasAnyPermission(user, item.permissions)
      ),
    })).filter((group) => group.items.length > 0);
  }

  const items = SIDEBAR_ITEMS.filter(
    (item) => !item.permissions || hasAnyPermission(user, item.permissions)
  ).map((item) => ({
    ...item,
    path: item.path || getRoleHomeRoute(user.role),
  }));

  return [{ section: "WORKSPACE", items }];
};
