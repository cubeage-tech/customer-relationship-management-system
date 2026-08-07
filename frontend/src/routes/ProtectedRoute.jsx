import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../core/hooks/useAuth';
import RoutePath from '../core/constants/routes.constant';

const ProtectedRoute = ({ allowedRoles }) => {
  const { user, isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to={RoutePath.LOGIN} replace />;
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to={RoutePath.HOME} replace />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
