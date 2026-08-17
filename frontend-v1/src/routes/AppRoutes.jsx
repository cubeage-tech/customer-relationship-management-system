import { Routes, Route } from "react-router-dom";

import MainLayout from "../layouts/MainLayout";
import DashboardLayout from "../layouts/DashboardLayout";
import ProtectedRoute from "./ProtectedRoute";

import RoutePath from "../core/constants/routes.constant";
import { USER_ROLES } from "../core/constants/app.constant";
import { PERMISSIONS } from "../core/constants/permission.constant";

// Public Pages
import Home from "../pages/public/Home";
import About from "../pages/public/About";
import Plans from "../pages/public/Plans";
import Contact from "../pages/public/Contact";
import Login from "../pages/auth/Login";
import Signup from "../pages/auth/Signup";

// Role Dashboards
import AdminDashboard from "../pages/dashboard/AdminDashboard";
import SalesManagerDashboard from "../pages/dashboard/SalesManagerDashboard";
import SalesExecutiveDashboard from "../pages/dashboard/SalesExecutiveDashboard";
import MarketingDashboard from "../pages/dashboard/MarketingDashboard";
import ServiceDashboard from "../pages/dashboard/ServiceDashboard";
import FinanceDashboard from "../pages/dashboard/FinanceDashboard";
import ExecutiveDashboard from "../pages/dashboard/ExecutiveDashboard";

// CRM Modules
import Customers from "../pages/customers/Customers";
import Leads from "../pages/leads/Leads";
import Opportunities from "../pages/opportunities/Opportunities";
import Quotations from "../pages/quotations/Quotations";
import ServiceTickets from "../pages/service/ServiceTickets";
import Campaigns from "../pages/marketing/Campaigns";
import Approvals from "../pages/finance/Approvals";
import Reports from "../pages/reports/Reports";

// Tenant Administration
import Users from "../pages/admin/Users";
import Roles from "../pages/admin/Roles";
import Settings from "../pages/admin/Settings";

// Common
import Profile from "../pages/common/Profile";
import Unauthorized from "../pages/common/Unauthorized";
import NotFound from "../pages/common/NotFound";

const AppRoutes = () => {
  return (
    <Routes>

      {/* ================= PUBLIC ROUTES ================= */}

      <Route element={<MainLayout />}>
        <Route path={RoutePath.HOME} element={<Home />} />
        <Route path={RoutePath.ABOUT} element={<About />} />
        <Route path={RoutePath.PLANS} element={<Plans />} />
        <Route path={RoutePath.CONTACT} element={<Contact />} />
        <Route path={RoutePath.LOGIN} element={<Login />} />
        <Route path={RoutePath.SIGNUP} element={<Signup />} />
        <Route path={RoutePath.UNAUTHORIZED} element={<Unauthorized />} />
        <Route path={RoutePath.NOT_FOUND} element={<NotFound />} />
      </Route>

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

        {/* ================= COMMON ================= */}

        <Route element={<ProtectedRoute />}>
          <Route path={RoutePath.PROFILE} element={<Profile />} />
        </Route>

      </Route>

    </Routes>
  );
};

export default AppRoutes;
