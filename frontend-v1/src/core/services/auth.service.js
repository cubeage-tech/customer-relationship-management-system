import ApiService from "./api.service";
import StorageService from "./storage.service";
import { APPLICATION_CONSTANTS } from "../constants/app.constant";
import { DEV_USERS } from "../mocks/devUsers";

const SHOW_MOCK_AUTH =
  import.meta.env.DEV && import.meta.env.VITE_ENABLE_MOCK_AUTH === "true";
const DEV_LOGIN_PASSWORD = import.meta.env.VITE_DEV_LOGIN_PASSWORD || "Dev@12345";

/**
 * Decode JWT token manually without external library
 */
const decodeJWT = (token) => {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (error) {
    return null;
  }
};

/**
 * Called by Login.jsx as `login({ email, password })`.
 * Returns { user, token } — Login.jsx destructures both and hands them
 * straight to useAuth().loginUser().
 */
export const login = async ({ email, password }) => {
  if (SHOW_MOCK_AUTH) {
    const devUser = DEV_USERS.find((u) => u.email === email);
    if (devUser && password === DEV_LOGIN_PASSWORD) {
      const token = "dev-mock-token"; // not a real JWT — fine for local dev only
      return { user: devUser, token };
    }
    // fall through to real API if no dev user matches, in case someone
    // types real credentials while mock mode is on
  }

  const data = await ApiService.login(email, password); // confirm this actually returns { user, token }
  return data;
};

/**
 * Called by Signup.jsx as `signup({ name, email, password, ... })`.
 */
export const signup = (data) => ApiService.signup(data);

/**
 * Called by VerifyEmail.jsx with the token from the emailed link's query string.
 */
export const verifyEmail = (token) => ApiService.verifyEmail({ token });

/**
 * Called by ForgotPassword.jsx. The backend answers the same way whether or not the email exists.
 */
export const forgotPassword = (email) => ApiService.forgotPassword({ email });

/**
 * Called by ResetPassword.jsx with the token from the emailed link's query string.
 */
export const resetPassword = (token, newPassword) => ApiService.resetPassword({ token, newPassword });

/**
 * Called by Login.jsx and VerifyEmail.jsx. The backend answers the same way whether or not a link was sent.
 */
export const resendVerification = (email) => ApiService.resendVerification({ email });

export class UserAuthService {
  static checkIsLoggedIn() {
    const token = StorageService.getData(APPLICATION_CONSTANTS.STORAGE.TOKEN);
    if (SHOW_MOCK_AUTH && token === "dev-mock-token") return true;
    if (token) {
      try {
        const decoded = decodeJWT(token);
        if (!decoded) {
          this.logoutUser();
          return false;
        }
        if (decoded.exp && decoded.exp < Date.now() / 1000) {
          this.logoutUser();
          return false;
        }
        return true;
      } catch {
        this.logoutUser();
        return false;
      }
    }
    return false;
  }

  static logoutUser() {
    StorageService.removeData(APPLICATION_CONSTANTS.STORAGE.TOKEN);
  }

  static getToken() {
    return StorageService.getData(APPLICATION_CONSTANTS.STORAGE.TOKEN);
  }
}