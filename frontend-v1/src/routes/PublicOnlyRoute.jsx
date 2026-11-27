import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../core/hooks/useAuth';
import { getRoleHomeRoute } from '../core/constants/routes.constant';

// Guards routes that only make sense for signed-out visitors (login, signup,
// verify-email). If a session already exists, bounce straight to that role's
// dashboard instead of rendering the auth page.
const PublicOnlyRoute = () => {
  const { user, isAuthenticated } = useAuth();

  if (isAuthenticated) {
    return <Navigate to={getRoleHomeRoute(user.role)} replace />;
  }

  return <Outlet />;
};

export default PublicOnlyRoute;