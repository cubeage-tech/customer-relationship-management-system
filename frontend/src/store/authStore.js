import { create } from 'zustand';
import { getStoredUser, setStoredUser, setToken, clearSession } from '../core/services/storage.service';

export const useAuthStore = create((set) => ({
  user: getStoredUser(),

  login: ({ user, token }) => {
    setToken(token);
    setStoredUser(user);
    set({ user });
  },

  logout: () => {
    clearSession();
    set({ user: null });
  },
}));
