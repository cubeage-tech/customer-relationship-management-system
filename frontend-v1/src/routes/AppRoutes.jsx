import { lazy, Suspense } from "react";
import { Routes, Route } from "react-router-dom";

import MainLayout from "../layouts/MainLayout";
import DashboardLayout from "../layouts/DashboardLayout";
import ProtectedRoute from "./ProtectedRoute";

import RoutePath from "../core/constants/routes.constant";
import { USER_ROLES } from "../core/constants/app.constant";
import { PERMISSIONS } from "../core/constants/permission.constant";

// Public Pages
const Home = lazy(() => import("../pages/public/Home"));
const About = lazy(() => import("../pages/public/About"));
const Plans = lazy(() => import("../pages/public/Plans"));
const Contact = lazy(() => import("../pages/public/Contact"));
const Login = lazy(() => import("../pages/auth/Login"));
const Signup = lazy(() => import("../pages/auth/Signup"));
const VerifyEmail = lazy(() => import("../pages/auth/VerifyEmail"));

// Role Dashboards
const SuperAdminDashboard = lazy(() => import("../pages/dashboard/ServiceDashboard"));
const AdminDashboard = lazy(() => import("../pages/dashboard/AdminDashboard"));
const SalesManagerDashboard = lazy(() => import("../pages/dashboard/SalesManagerDashboard"));
const SalesExecutiveDashboard = lazy(() => import("../pages/dashboard/SalesExecutiveDashboard"));
const MarketingDashboard = lazy(() => import("../pages/dashboard/MarketingDashboard"));
const ServiceDashboard = lazy(() => import("../pages/dashboard/ServiceAgentDashboard"));
const FinanceDashboard = lazy(() => import("../pages/dashboard/FinanceDashboard"));
const ExecutiveDashboard = lazy(() => import("../pages/dashboard/ExecutiveDashboard"));

// CRM Modules
const Customers = lazy(() => import("../pages/customers/Customers"));
const CustomerDetail = lazy(() => import("../pages/customers/CustomerDetail"));
const Leads = lazy(() => import("../pages/leads/Leads"));
const LeadDetail = lazy(() => import("../pages/leads/LeadDetail"));
const Opportunities = lazy(() => import("../pages/opportunities/Opportunities"));
const OpportunityDetail = lazy(() => import("../pages/opportunities/OpportunityDetail"));
const Quotations = lazy(() => import("../pages/quotations/Quotations"));
const QuotationDetail = lazy(() => import("../pages/quotations/QuotationDetail"));
const ServiceTickets = lazy(() => import("../pages/service/ServiceTickets"));
const Campaigns = lazy(() => import("../pages/marketing/Campaigns"));
const Approvals = lazy(() => import("../pages/finance/Approvals"));
const Reports = lazy(() => import("../pages/reports/Reports"));

// Tenant Administration
const Users = lazy(() => import("../pages/admin/Users"));
const Roles = lazy(() => import("../pages/admin/Roles"));
const Settings = lazy(() => import("../pages/admin/Settings"));

// Platform Administration (super_admin)
const PlatformTenants = lazy(() => import("../pages/platform/Tenants"));
const SubscriptionPlans = lazy(() => import("../pages/platform/SubscriptionPlans"));
const RolesPermissions = lazy(() => import("../pages/platform/RolesPermissions"));
const PlatformSettings = lazy(() => import("../pages/platform/PlatformSettings"));
const PlatformReports = lazy(() => import("../pages/platform/PlatformReports"));
const AuditLogs = lazy(() => import("../pages/platform/AuditLogs"));

// Common
const Profile = lazy(() => import("../pages/common/Profile"));
const HelpCenter = lazy(() => import("../pages/common/HelpCenter"));
const Unauthorized = lazy(() => import("../pages/common/Unauthorized"));
const NotFound = lazy(() => import("../pages/common/NotFound"));

/** Full-page fallback shown while a route's chunk is being fetched. */
const PageLoader = () => (
  <div className="flex min-h-[60vh] w-full items-center justify-center" role="status" aria-label="Loading">
    <span className="h-8 w-8 animate-spin rounded-full border-2 border-border border-t-primary" />
  </div>
);

const AppRoutes = () => {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>

        {/* ================= PUBLIC ROUTES ================= */}

        <Route element={<MainLayout />}>
          <Route path={RoutePath.HOME} element={<Home />} />
          <Route path={RoutePath.ABOUT} element={<About />} />
          <Route path={RoutePath.PLANS} element={<Plans />} />
          <Route path={RoutePath.CONTACT} element={<Contact />} />
          <Route path={RoutePath.UNAUTHORIZED} element={<Unauthorized />} />
          <Route path={RoutePath.NOT_FOUND} element={<NotFound />} />
        </Route>

        {/* ================= AUTH ROUTES ================= */}
        <Route path={RoutePath.LOGIN} element={<Login />} />
        <Route path={RoutePath.SIGNUP} element={<Signup />} />
        <Route path={RoutePath.VERIFY_EMAIL} element={<VerifyEmail />} />


        {/* ================= DASHBOARD LAYOUT ================= */}

        <Route element={<DashboardLayout />}>

          {/* ================= ROLE DASHBOARDS ================= */}

          <Route
            element={
              <ProtectedRoute
                allowedRoles={[USER_ROLES.SUPER_ADMIN]}
              />
            }
          >
            <Route
              path={RoutePath.SUPER_ADMIN_DASHBOARD}
              element={<SuperAdminDashboard />}
            />
          </Route>

          <Route
            element={
              <ProtectedRoute
                allowedRoles={[USER_ROLES.ADMIN]}
              />
            }
          >
            <Route
              path={RoutePath.ADMIN_DASHBOARD}
              element={<AdminDashboard />}
            />
          </Route>

          <Route
            element={
              <ProtectedRoute
                allowedRoles={[USER_ROLES.SALES_MANAGER]}
              />
            }
          >
            <Route
              path={RoutePath.SALES_MANAGER_DASHBOARD}
              element={<SalesManagerDashboard />}
            />
          </Route>

          <Route
            element={
              <ProtectedRoute
                allowedRoles={[USER_ROLES.SALES_EXECUTIVE]}
              />
            }
          >
            <Route
              path={RoutePath.SALES_EXECUTIVE_DASHBOARD}
              element={<SalesExecutiveDashboard />}
            />
          </Route>

          <Route
            element={
              <ProtectedRoute
                allowedRoles={[USER_ROLES.MARKETING_EXECUTIVE]}
              />
            }
          >
            <Route
              path={RoutePath.MARKETING_DASHBOARD}
              element={<MarketingDashboard />}
            />
          </Route>

          <Route
            element={
              <ProtectedRoute
                allowedRoles={[USER_ROLES.SERVICE_AGENT]}
              />
            }
          >
            <Route
              path={RoutePath.SERVICE_DASHBOARD}
              element={<ServiceDashboard />}
            />
          </Route>

          <Route
            element={
              <ProtectedRoute
                allowedRoles={[USER_ROLES.FINANCE_APPROVER]}
              />
            }
          >
            <Route
              path={RoutePath.FINANCE_DASHBOARD}
              element={<FinanceDashboard />}
            />
          </Route>

          <Route
            element={
              <ProtectedRoute
                allowedRoles={[USER_ROLES.EXECUTIVE_OWNER]}
              />
            }
          >
            <Route
              path={RoutePath.EXECUTIVE_DASHBOARD}
              element={<ExecutiveDashboard />}
            />
          </Route>

          {/* ================= CUSTOMERS ================= */}

          <Route
            element={
              <ProtectedRoute
                requiredPermissions={[PERMISSIONS.CUSTOMERS_VIEW]}
              />
            }
          >
            <Route path={RoutePath.CUSTOMERS} element={<Customers />} />
            <Route path={RoutePath.EDIT_CUSTOMER} element={<CustomerDetail />} />
          </Route>

          {/* ================= LEADS ================= */}

          <Route
            element={
              <ProtectedRoute
                requiredPermissions={[PERMISSIONS.LEADS_VIEW]}
              />
            }
          >
            <Route path={RoutePath.LEADS} element={<Leads />} />
            <Route path={RoutePath.EDIT_LEAD} element={<LeadDetail />} />
          </Route>

          {/* ================= OPPORTUNITIES ================= */}

          <Route
            element={
              <ProtectedRoute
                requiredPermissions={[PERMISSIONS.OPPORTUNITIES_VIEW]}
              />
            }
          >
            <Route path={RoutePath.OPPORTUNITIES} element={<Opportunities />} />
            <Route path={RoutePath.EDIT_OPPORTUNITY} element={<OpportunityDetail />} />
          </Route>

          {/* ================= QUOTATIONS ================= */}

          <Route
            element={
              <ProtectedRoute
                requiredPermissions={[PERMISSIONS.QUOTATIONS_VIEW]}
              />
            }
          >
            <Route path={RoutePath.QUOTATIONS} element={<Quotations />} />
            <Route path={RoutePath.EDIT_QUOTATION} element={<QuotationDetail />} />
          </Route>

          {/* ================= SERVICE ================= */}

          <Route
            element={
              <ProtectedRoute
                requiredPermissions={[
                  PERMISSIONS.TICKETS_VIEW,
                  PERMISSIONS.TICKETS_CREATE,
                ]}
              />
            }
          >
            <Route path={RoutePath.SERVICE_TICKETS} element={<ServiceTickets />} />
          </Route>

          {/* ================= MARKETING ================= */}

          <Route
            element={
              <ProtectedRoute
                requiredPermissions={[PERMISSIONS.CAMPAIGNS_VIEW]}
              />
            }
          >
            <Route path={RoutePath.CAMPAIGNS} element={<Campaigns />} />
          </Route>

          {/* ================= FINANCE / APPROVALS ================= */}

          <Route
            element={
              <ProtectedRoute
                requiredPermissions={[
                  PERMISSIONS.QUOTATIONS_APPROVE,
                  PERMISSIONS.QUOTATIONS_APPROVE_DISCOUNT,
                ]}
              />
            }
          >
            <Route path={RoutePath.APPROVALS} element={<Approvals />} />
          </Route>

          {/* ================= REPORTS ================= */}

          <Route
            element={
              <ProtectedRoute
                requiredPermissions={[PERMISSIONS.REPORTS_VIEW]}
              />
            }
          >
            <Route path={RoutePath.REPORTS} element={<Reports />} />
          </Route>

          {/* ================= TENANT ADMINISTRATION ================= */}

          <Route
            element={
              <ProtectedRoute
                requiredPermissions={[PERMISSIONS.USERS_VIEW]}
              />
            }
          >
            <Route path={RoutePath.ADMIN_USERS} element={<Users />} />
            <Route path={RoutePath.ADMIN_ROLES} element={<Roles />} />
          </Route>

          <Route
            element={
              <ProtectedRoute
                requiredPermissions={[PERMISSIONS.SETTINGS_VIEW]}
              />
            }
          >
            <Route path={RoutePath.ADMIN_SETTINGS} element={<Settings />} />
          </Route>

          {/* ================= PLATFORM ADMINISTRATION ================= */}

          <Route
            element={
              <ProtectedRoute
                allowedRoles={[USER_ROLES.SUPER_ADMIN]}
              />
            }
          >
            <Route path={RoutePath.PLATFORM_TENANTS} element={<PlatformTenants />} />
            <Route path={RoutePath.PLATFORM_SUBSCRIPTION_PLANS} element={<SubscriptionPlans />} />
            <Route path={RoutePath.PLATFORM_ROLES_PERMISSIONS} element={<RolesPermissions />} />
            <Route path={RoutePath.PLATFORM_SETTINGS} element={<PlatformSettings />} />
            <Route path={RoutePath.PLATFORM_REPORTS} element={<PlatformReports />} />
            <Route path={RoutePath.PLATFORM_AUDIT_LOGS} element={<AuditLogs />} />
          </Route>

          {/* ================= COMMON ================= */}

          <Route element={<ProtectedRoute />}>
            <Route path={RoutePath.PROFILE} element={<Profile />} />
            <Route path={RoutePath.HELP_CENTER} element={<HelpCenter />} />
          </Route>

        </Route>

      </Routes>
    </Suspense>
  );
};

export default AppRoutes;
