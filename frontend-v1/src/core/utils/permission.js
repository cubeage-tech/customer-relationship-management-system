import { ROLE_LABELS, USER_ROLES } from '../constants/app.constant';
import {
  DATA_SCOPE,
  ROLE_DATA_SCOPE,
  ROLE_PERMISSIONS,
  SCOPE_LABELS,
} from '../constants/permission.constant';

/** All permissions granted to a role. */
export const getRolePermissions = (role) => ROLE_PERMISSIONS[role] || [];

/** True when the user's role grants the given permission. */
export const hasPermission = (user, permission) => {
  if (!user?.role || !permission) return false;
  return getRolePermissions(user.role).includes(permission);
};

/** True when the user's role grants at least one of the given permissions. */
export const hasAnyPermission = (user, permissions = []) =>
  permissions.some((permission) => hasPermission(user, permission));

/** True when the user's role grants every one of the given permissions. */
export const hasAllPermissions = (user, permissions = []) =>
  permissions.every((permission) => hasPermission(user, permission));

/** True when the user holds one of the given internal role names. */
export const hasRole = (user, roles = []) => {
  if (!user?.role) return false;
  const allowed = Array.isArray(roles) ? roles : [roles];
  return allowed.includes(user.role);
};

export const isSuperAdmin = (user) => user?.role === USER_ROLES.SUPER_ADMIN;

/** How much data of a module the role may read. */
export const getDataScope = (role, module) =>
  ROLE_DATA_SCOPE[role]?.[module] || DATA_SCOPE.NONE;

export const getDataScopeLabel = (role, module) =>
  SCOPE_LABELS[getDataScope(role, module)];

/** Display name for an internal role name. */
export const getRoleLabel = (role) => ROLE_LABELS[role] || 'Unknown role';
