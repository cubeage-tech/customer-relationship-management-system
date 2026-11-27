import ApiService from './api.service';

/** SEC-1c: the code (or a recovery code) after the password → { token, user }. */
export const verifyMfa = (mfaToken, code) => ApiService.verifyMfa(mfaToken, code);

/** SEC-1c: first sign-in of a role that requires it → { secret, otpauthUri }. */
export const startMfaSetup = (mfaToken) => ApiService.startMfaSetup(mfaToken);

/** → { token, user, recoveryCodes } */
export const confirmMfaSetup = (mfaToken, code) => ApiService.confirmMfaSetup(mfaToken, code);

/** My two-step sign-in: { enabled, required, recoveryCodesLeft, enabledAt }. */
export const getMfaStatus = () => ApiService.getMfaStatus();

export const beginMfaSetup = () => ApiService.beginMfaSetup();

/** → { recoveryCodes } */
export const enableMfa = (code) => ApiService.enableMfa(code);

export const disableMfa = (code) => ApiService.disableMfa(code);

/** → { recoveryCodes } */
export const regenerateRecoveryCodes = (code) => ApiService.regenerateRecoveryCodes(code);

/** Admin: reset a user's two-step sign-in (lost phone). */
export const resetUserMfa = (userId) => ApiService.resetUserMfa(userId);
