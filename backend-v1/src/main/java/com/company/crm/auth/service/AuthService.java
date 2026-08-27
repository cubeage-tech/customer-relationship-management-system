package com.company.crm.auth.service;

import com.company.crm.auth.dto.request.AdminSignupRequest;
import com.company.crm.auth.dto.request.LoginRequest;
import com.company.crm.auth.dto.response.AuthResponse;
import com.company.crm.auth.dto.response.UserSummaryResDto;
import com.company.crm.auth.entity.LoginAttempt;
import com.company.crm.auth.entity.PasswordResetToken;
import com.company.crm.auth.mapper.AuthMapper;
import com.company.crm.auth.repository.LoginAttemptRepository;
import com.company.crm.auth.repository.PasswordResetTokenRepository;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.security.JwtService;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.user.entity.Role;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.RoleRepository;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final int PASSWORD_RESET_EXPIRY_HOURS = 1;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthMapper authMapper;
    private final EmailVerificationTokenService emailVerificationTokenService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordAttempt(user, request.getEmail(), false,
                    user == null ? "invalid_email" : "invalid_password");
            throw ApiException.unauthorized("Invalid email or password");
        }

        if (user.getStatus() != AccountStatus.ACTIVE) {
            recordAttempt(user, request.getEmail(), false, "account_" + user.getStatus().getDbValue());
            throw ApiException.forbidden(statusMessage(user.getStatus()));
        }

        recordAttempt(user, request.getEmail(), true, null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateAccessToken(user.getEmail(), Map.of(
                "userId", user.getId(),
                "role", user.getRole().getName().getDbValue(),
                "tenantId", user.getTenant() != null ? user.getTenant().getId() : -1
        ));

        return authMapper.toAuthResponse(token, user);
    }

    @Transactional
    public UserSummaryResDto signupAdmin(AdminSignupRequest request) {
        if (request.getRole() != null && !"admin".equals(request.getRole())) {
            throw ApiException.badRequest("Public signup can only create a tenant admin account");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw ApiException.conflict("A user with this email already exists");
        }

        Tenant tenant = new Tenant();
        tenant.setCompanyName(request.getOrganizationName());
        tenant.setLegalName(request.getAddress());
        tenant.setBankAccountNumber(request.getBankAccountNumber());
        tenant = tenantRepository.save(tenant);

        Role adminRole = roleRepository.findByName(RoleType.ADMIN)
                .orElseThrow(() -> ApiException.badRequest("admin role is not seeded"));

        User user = new User();
        user.setTenant(tenant);
        user.setRole(adminRole);
        user.setFullName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(AccountStatus.PENDING_VERIFICATION);
        user = userRepository.save(user);

        emailVerificationTokenService.issueToken(user);

        return authMapper.toSummary(user);
    }

    public void verifyEmail(String token) {
        emailVerificationTokenService.verify(token);
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Don't reveal whether the email is registered.
            return;
        }

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(LocalDateTime.now().plusHours(PASSWORD_RESET_EXPIRY_HOURS));
        passwordResetTokenRepository.save(token);

        log.info("Password reset token for {}: {} (expires in {}h)", user.getEmail(), token.getToken(), PASSWORD_RESET_EXPIRY_HOURS);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> ApiException.badRequest("Invalid reset token"));

        if (token.getUsedAt() != null) {
            throw ApiException.badRequest("This reset link has already been used");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("This reset link has expired");
        }

        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void recordAttempt(User user, String emailAttempted, boolean successful, String failureReason) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUser(user);
        attempt.setEmailAttempted(emailAttempted);
        attempt.setSuccessful(successful);
        attempt.setFailureReason(failureReason);
        loginAttemptRepository.save(attempt);
    }

    private String statusMessage(AccountStatus status) {
        return switch (status) {
            case PENDING_VERIFICATION -> "Please verify your email before logging in";
            case SUSPENDED -> "This account has been suspended";
            case DEACTIVATED -> "This account has been deactivated";
            case ACTIVE -> "Account is active";
        };
    }
}
