import StorageService from "./storage.service";
import { APPLICATION_CONSTANTS } from "../constants/app.constant";

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

export class UserAuthService {
  static checkIsLoggedIn() {
    const token = StorageService.getData(APPLICATION_CONSTANTS.STORAGE.TOKEN);
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
