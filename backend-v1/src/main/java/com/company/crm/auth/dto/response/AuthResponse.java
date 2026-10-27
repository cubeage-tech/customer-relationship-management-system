package com.company.crm.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * The sign-in result: an access token, a refresh token (API-G01, trade it at /auth/refresh when the
 * access token expires) and the user — or (SEC-1c) a second step: {@code mfaRequired} (enter a code)
 * or {@code mfaSetupRequired} (set up the authenticator app first), with the {@code mfaToken} to
 * continue with. {@code recoveryCodes} come once, when two-step sign-in is set up.
 */
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;
    private UserSummaryResDto user;
    private boolean mfaRequired;
    private boolean mfaSetupRequired;
    private String mfaToken;
    private List<String> recoveryCodes;

    public static AuthResponse signedIn(String token, String refreshToken, UserSummaryResDto user) {
        return new AuthResponse(token, refreshToken, user, false, false, null, null);
    }

    public static AuthResponse secondStep(boolean setup, String mfaToken) {
        return new AuthResponse(null, null, null, !setup, setup, mfaToken, null);
    }

    public AuthResponse withRecoveryCodes(List<String> codes) {
        return new AuthResponse(token, refreshToken, user, mfaRequired, mfaSetupRequired, mfaToken, codes);
    }
}
