import { create } from 'zustand';
import StorageService from '../core/services/storage.service';
import { APPLICATION_CONSTANTS } from '../core/constants/app.constant';

const { TOKEN, USER_DETAILS } = APPLICATION_CONSTANTS.STORAGE;

export const useAuthStore = create((set) => ({
  user: StorageService.getData(USER_DETAILS),

  // rememberMe: true -> localStorage (persists across browser restarts)
  // rememberMe: false -> sessionStorage (cleared when tab closes) — matches
  // the session-detection logic already in interceptor.service.js
  login: ({ user, token, rememberMe = false }) => {
    const opts = { session: !rememberMe };
    StorageService.setData(TOKEN, token, opts);
    StorageService.setData(USER_DETAILS, user, opts);
    set({ user });
  },

  logout: () => {
    StorageService.removeData(TOKEN);
    StorageService.removeData(USER_DETAILS);
    set({ user: null });
  },
}));