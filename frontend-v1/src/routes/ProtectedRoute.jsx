import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../core/hooks/useAuth';
import RoutePath from '../core/constants/routes.constant';
import { hasAnyPermission, hasRole } from '../core/utils/permission';

/**
 * Guards a CRM route.
 *
 * @param {string[]} [allowedRoles] internal role names allowed through
 * @param {string[]} [requiredPermissions] any one of these permissions grants access
 */
const ProtectedRoute = ({ allowedRoles, requiredPermissions }) => {
  const { user, isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to={RoutePath.LOGIN} replace />;
  }

  if (allowedRoles && !hasRole(user, allowedRoles)) {
    return <Navigate to={RoutePath.UNAUTHORIZED} replace />;
  }

  if (requiredPermissions && !hasAnyPermission(user, requiredPermissions)) {
    return <Navigate to={RoutePath.UNAUTHORIZED} replace />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
