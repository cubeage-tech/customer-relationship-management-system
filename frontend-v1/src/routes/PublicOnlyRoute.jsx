import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../core/hooks/useAuth';
import { getRoleHomeRoute } from '../core/constants/routes.constant';

// Guards routes that only make sense for signed-out visitors (login, signup,
// verify-email). If a session already exists, bounce straight to that role's
// dashboard instead of rendering the auth page.
const PublicOnlyRoute = () => {
  const { user, isLoading } = useAuth();

  // Wait for auth state to hydrate (e.g. token check on app load) before
  // deciding — otherwise a logged-in user briefly flashes the login form.
  if (isLoading) return null; // or a spinner/skeleton

  if (user) {
    return <Navigate to={getRoleHomeRoute(user.role)} replace />;
  }

  return <Outlet />;
};

export default PublicOnlyRoute;