                                                                                                                                                                                                                                                                                                                                                                        package com.company.crm.auth.controller;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import com.company.crm.auth.mfa.MfaService;
import com.company.crm.auth.password.PasswordPolicy;
import com.company.crm.auth.dto.request.*;
import com.company.crm.auth.dto.response.AuthResponse;
import com.company.crm.auth.dto.response.UserSummaryResDto;
import com.company.crm.auth.service.AuthService;
import com.company.crm.common.response.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordPolicy passwordPolicy;

    @PostMapping("/login")
    public Response<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Response.ok(authService.login(request));
    }

    /** SEC-1c: the authenticator code (or a recovery code) after the password. */
    @PostMapping("/mfa/verify")
    public Response<AuthResponse> verifyMfa(@Valid @RequestBody MfaCodeRequest request) {
        return Response.ok("Signed in", authService.verifyMfa(request.mfaToken(), request.code()));
    }

    /** SEC-1c: first sign-in of a role that requires two-step sign-in — the secret to add to the app. */
    @PostMapping("/mfa/setup")
    public Response<MfaService.Setup> startMfaSetup(@Valid @RequestBody MfaTokenRequest request) {
        return Response.ok(authService.startMfaSetup(request.mfaToken()));
    }

    /** SEC-1c: confirm the app with a code; signs in and returns the recovery codes (shown once). */
    @PostMapping("/mfa/setup/confirm")
    public Response<AuthResponse> confirmMfaSetup(@Valid @RequestBody MfaCodeRequest request) {
        return Response.ok("Two-step sign-in is on", authService.confirmMfaSetup(request.mfaToken(), request.code()));
    }

    public record MfaTokenRequest(@NotBlank String mfaToken) {}

    public record MfaCodeRequest(@NotBlank String mfaToken, @NotBlank @Size(max = 20) String code) {}

    @PostMapping("/signup")
    public Response<UserSummaryResDto> signup(@Valid @RequestBody AdminSignupRequest request) {
        return Response.ok(
                "Account created. Please check your email to verify your account before logging in.",
                authService.signupAdmin(request));
    }

    /** SEC-1b: the password rules, so sign-up and reset forms can show them as the user types. */
    @GetMapping("/password-policy")
    public Response<PasswordPolicy.Rules> passwordPolicy() {
        return Response.ok(passwordPolicy.rules());
    }

    /** API-G01: ends this device's session (its refresh token); the short-lived access token expires on its own. */
    @PostMapping("/logout")
    public Response<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request != null ? request.refreshToken() : null);
        return Response.ok("Logged out", null);
    }

    /** API-G01: a new access token for a refresh token (which is replaced by a new one). */
    @PostMapping("/refresh")
    public Response<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Response.ok(authService.refresh(request.refreshToken()));
    }

    public record RefreshRequest(@NotBlank String refreshToken) {}

    @PostMapping("/verify-email")
    public Response<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.getToken());
        return Response.ok("Email verified. You can now log in.", null);
    }

    @PostMapping("/resend-verification")
    public Response<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.getEmail());
        return Response.ok("If that account is awaiting verification, a new link has been sent.", null);
    }

    @PostMapping("/forgot-password")
    public Response<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return Response.ok("If that email is registered, a reset link has been sent.", null);
    }

    @PostMapping("/reset-password")
    public Response<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return Response.ok("Password updated. You can now log in.", null);
    }
}
