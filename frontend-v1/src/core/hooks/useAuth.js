import { useEffect } from 'react';
import { useAuthStore } from '../../store/authStore';
import { UserAuthService } from '../services/auth.service';

export const useAuth = () => {
  const user = useAuthStore((state) => state.user);
  // A cached user only counts while its token is present and unexpired.
  const isAuthenticated = useAuthStore((state) => Boolean(state.user)) && UserAuthService.checkIsLoggedIn();
  const loginUser = useAuthStore((state) => state.login);
  const logoutUser = useAuthStore((state) => state.logout);

  // Drop the stale user too, so user and token are cleared together.
  useEffect(() => {
    if (user && !isAuthenticated) logoutUser();
  }, [user, isAuthenticated, logoutUser]);

  return { user, isAuthenticated, loginUser, logoutUser };
};
