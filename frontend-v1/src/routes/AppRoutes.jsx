import { lazy, Suspense } from "react";
import { Routes, Route } from "react-router-dom";
import RoutePath from "../core/constants/routes.constant";
import ProtectedRoute from "./ProtectedRoute";
import { USER_ROLES } from "../core/constants/app.constant";
import ErrorBoundary from "../components/common/ErrorBoundary";

// Auth pages
const Login          = lazy(() => import("../pages/auth/Login"));
const Register       = lazy(() => import("../pages/auth/Register"));

// Other
const Unauthorized = lazy(() => import("../pages/Unauthorized"));

// ─── Loading Fallbacks ────────────────────────────────────────────────────────
const LoadingFallback = () => (
  <div className="flex items-center justify-center h-screen bg-gray-50">
    <div className="text-center">
      <div className="inline-block w-12 h-12 mb-4 border-t-2 border-b-2 border-blue-500 rounded-full animate-spin" />
      <p className="font-medium text-gray-600">Loading...</p>
    </div>
  </div>
);

const PageFallback = () => (
  <div className="flex items-center justify-center min-h-[60vh]">
    <div className="inline-block w-8 h-8 border-t-2 border-b-2 border-blue-500 rounded-full animate-spin" />
  </div>
);

// ─── S: Suspense + ErrorBoundary per route ────────────────────────────────────
// Each lazy route is wrapped in both Suspense (loading state) and ErrorBoundary
// (runtime error isolation). A crash in one page no longer takes down the app.
const S = ({ children }) => (
  <ErrorBoundary>
    <Suspense fallback={<PageFallback />}>{children}</Suspense>
  </ErrorBoundary>
);

const AppRoutes = () => {
  return (
    <Suspense fallback={<LoadingFallback />}>
      <Routes>
        <Route path={RoutePath.AUTH_LOGIN}          element={<S><Login /></S>} />
        <Route path={RoutePath.AUTH_REGISTER}        element={<S><Register /></S>} />

        {/* ── Admin routes ── */}
        <Route element={<ProtectedRoute allowedRoles={[USER_ROLES.ADMIN]} />}>
        </Route>

        <Route path={RoutePath.UNAUTHORIZED} element={<S><Unauthorized /></S>} />
      </Routes>
    </Suspense>
  );
};

export default AppRoutes;
