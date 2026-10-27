import { useEffect } from 'react';
import { useAuthStore } from '../../store/authStore';
import { USER_ROLES } from '../../core/constants/app.constant';
import { getMyRolePermissions } from '../../core/services/rolePermission.service';

/**
 * ROLE-8: loads the permissions the tenant switched off for the signed-in user's role, so menus and
 * buttons match what the API allows. Refreshed when the window regains focus.
 */
const PermissionSync = () => {
  const userId = useAuthStore((state) => state.user?.id ?? state.user?.email ?? null);
  const role = useAuthStore((state) => state.user?.role ?? null);
  const setRevoked = useAuthStore((state) => state.setRevokedPermissions);

  useEffect(() => {
    if (!userId || !role || role === USER_ROLES.SUPER_ADMIN) return undefined;
    let active = true;
    const load = () => getMyRolePermissions()
      .then((mine) => { if (active) setRevoked(mine?.revoked ?? []); })
      .catch(() => {});
    load();
    window.addEventListener('focus', load);
    return () => {
      active = false;
      window.removeEventListener('focus', load);
    };
  }, [userId, role, setRevoked]);

  return null;
};

export default PermissionSync;
