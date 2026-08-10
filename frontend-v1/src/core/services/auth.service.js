import { apiPost } from './api.service';
import { DEV_USERS } from '../mocks/devUsers';
import { NOTIFICATION_MESSAGES } from '../constants/notification.constant';

// `import.meta.env.DEV` is a build-time constant Vite hardcodes to `false` in
// production builds, so this branch — and the DEV_USERS list it closes over —
// is dead-code-eliminated from `vite build` output no matter how the env vars
// below are set. Mock login can only ever run under `vite dev`.
const MOCK_AUTH_ENABLED =
  import.meta.env.DEV && import.meta.env.VITE_ENABLE_MOCK_AUTH === 'true';
const DEV_LOGIN_PASSWORD = import.meta.env.VITE_DEV_LOGIN_PASSWORD || 'Dev@12345';

const mockLogin = ({ email, password }) => {
  const user = DEV_USERS.find(
    (candidate) => candidate.email.toLowerCase() === String(email ?? '').toLowerCase()
  );

  if (!user || password !== DEV_LOGIN_PASSWORD) {
    return Promise.reject(new Error(NOTIFICATION_MESSAGES.LOGIN_FAILED));
  }

  return Promise.resolve({ user, token: `dev-token.${user.id}` });
};

export const login = (credentials) =>
  MOCK_AUTH_ENABLED ? mockLogin(credentials) : apiPost('/auth/login', credentials);

export const signup = (payload) => apiPost('/auth/signup', payload);

export const logout = () => apiPost('/auth/logout');
