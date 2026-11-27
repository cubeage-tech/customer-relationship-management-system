import ApiService from './api.service';

/** ROLE-8 (admin): each role's default permissions and which are switched off in this tenant. */
export const listRolePermissions = () => ApiService.getRolePermissionSettings();

/** ROLE-8: my role's permissions as customised by the tenant ({ revoked, effective }). */
export const getMyRolePermissions = () => ApiService.getMyRolePermissions();

export const updateRolePermissions = (role, revoked) => ApiService.updateRolePermissions(role, revoked);

/** NOT-1: { weeklyDigest } */
export const getNotificationPreferences = () => ApiService.getNotificationPreferences();

export const saveNotificationPreferences = (data) => ApiService.saveNotificationPreferences(data);
