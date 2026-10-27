import { create } from 'zustand';
import StorageService from '../core/services/storage.service';
import { APPLICATION_CONSTANTS } from '../core/constants/app.constant';
import { queryClient } from '../core/query/queryClient';
import ApiService from '../core/services/api.service';

const { TOKEN, REFRESH_TOKEN, USER_DETAILS } = APPLICATION_CONSTANTS.STORAGE;

export const useAuthStore = create((set) => ({
  user: StorageService.getData(USER_DETAILS),

  // rememberMe: true -> localStorage (persists across browser restarts)
  // rememberMe: false -> sessionStorage (cleared when tab closes) — matches
  // the session-detection logic already in interceptor.service.js
  login: ({ user, token, refreshToken, rememberMe = false }) => {
    const opts = { session: !rememberMe };
    StorageService.setData(TOKEN, token, opts);
    // API-G01: kept alongside the access token, in the same storage.
    if (refreshToken) StorageService.setData(REFRESH_TOKEN, refreshToken, opts);
    StorageService.setData(USER_DETAILS, user, opts);
    set({ user });
  },

  // ROLE-8: the tenant's switched-off permissions for this user's role (kept in memory, refetched on load).
  setRevokedPermissions: (revoked) =>
    set((state) => (state.user ? { user: { ...state.user, revokedPermissions: revoked } } : {})),

  logout: () => {
    // API-G01: end this device's session on the server too (best effort — signing out never waits on it).
    const refreshToken = StorageService.getData(REFRESH_TOKEN);
    if (refreshToken) ApiService.logout(refreshToken).catch(() => {});
    // Drop cached server data so the next user never sees the previous user's results.
    queryClient.clear();
    StorageService.removeData(TOKEN);
    StorageService.removeData(REFRESH_TOKEN);
    StorageService.removeData(USER_DETAILS);
    set({ user: null });
  },
}));