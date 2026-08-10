import { useMemo } from 'react';
import { useAuth } from './useAuth';
import {
  getDataScope,
  getRoleLabel,
  getRolePermissions,
  hasAllPermissions,
  hasAnyPermission,
  hasPermission,
  hasRole,
} from '../utils/permission';

/** Permission helpers bound to the currently authenticated CRM user. */
export const usePermissions = () => {
  const { user } = useAuth();

  return useMemo(
    () => ({
      role: user?.role || null,
      roleLabel: user?.role ? getRoleLabel(user.role) : '',
      permissions: getRolePermissions(user?.role),
      can: (permission) => hasPermission(user, permission),
      canAny: (permissions) => hasAnyPermission(user, permissions),
      canAll: (permissions) => hasAllPermissions(user, permissions),
      is: (roles) => hasRole(user, roles),
      scopeFor: (module) => getDataScope(user?.role, module),
    }),
    [user]
  );
};
