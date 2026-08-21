                                                                                                                                                                                                                                                                                                                                                                        package com.company.crm.auth.controller;

import com.company.crm.auth.dto.request.*;
import com.company.crm.auth.dto.response.AuthResponse;
import com.company.crm.auth.dto.response.UserSummaryResDto;
import com.company.crm.auth.service.AuthService;
import com.company.crm.common.response.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Response<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Response.ok(authService.login(request));
    }

    @PostMapping("/signup")
    public Response<UserSummaryResDto> signup(@Valid @RequestBody AdminSignupRequest request) {
        return Response.ok(
                "Account created. Please check your email to verify your account before logging in.",
                authService.signupAdmin(request));
    }

    @PostMapping("/logout")
    public Response<Void> logout() {
        // Stateless JWT — nothing to invalidate server-side yet (no refresh-token rotation in this cut).
        return Response.ok("Logged out", null);
    }

    @PostMapping("/verify-email")
    public Response<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.getToken());
        return Response.ok("Email verified. You can now log in.", null);
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
