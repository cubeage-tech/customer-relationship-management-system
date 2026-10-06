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
import com.company.crm.common.exception.InvalidTokenException;
import com.company.crm.common.exception.TenantInactiveException;
import com.company.crm.common.exception.TokenExpiredException;
import com.company.crm.common.mail.MailService;
import com.company.crm.common.security.JwtService;
import com.company.crm.subscription.service.SubscriptionService;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.user.entity.Role;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.RoleRepository;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final int PASSWORD_RESET_EXPIRY_HOURS = 1;
    private static final int LOGIN_MAX_FAILURES = 5;
    private static final int PASSWORD_RESET_MAX_REQUESTS = 3;
    private static final int THROTTLE_WINDOW_MINUTES = 15;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthMapper authMapper;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final SubscriptionService subscriptionService;
    private final MailService mailService;

    // noRollbackFor: failed attempts must still be recorded even though we then throw.
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request) {
        long recentFailures = loginAttemptRepository.countByEmailAttemptedAndSuccessfulFalseAndAttemptedAtAfter(
                request.getEmail(), LocalDateTime.now().minusMinutes(THROTTLE_WINDOW_MINUTES));
        if (recentFailures >= LOGIN_MAX_FAILURES) {
            throw ApiException.tooManyRequests("Too many failed sign-in attempts. Try again in "
                    + THROTTLE_WINDOW_MINUTES + " minutes.");
        }

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordAttempt(user, request.getEmail(), false,
                    user == null ? "invalid_email" : "invalid_password");
            throw ApiException.unauthorized("Invalid email or password");
        }

        if (user.getStatus() != AccountStatus.ACTIVE) {
            recordAttempt(user, request.getEmail(), false, "account_" + user.getStatus().getDbValue());
            // Error code lets the login screen offer "resend verification email" for this case only.
            throw new ApiException(HttpStatus.FORBIDDEN, statusMessage(user.getStatus()),
                    user.getStatus() == AccountStatus.PENDING_VERIFICATION ? "EMAIL_NOT_VERIFIED" : null);
        }

        Tenant tenant = user.getTenant();
        if (tenant != null && tenant.getStatus() != AccountStatus.ACTIVE) {
            recordAttempt(user, request.getEmail(), false, "tenant_" + tenant.getStatus().getDbValue());
            throw new TenantInactiveException("Your company account has been "
                    + tenant.getStatus().getDbValue().replace('_', ' ') + ". Please contact support.");
        }

        recordAttempt(user, request.getEmail(), true, null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("role", user.getRole().getName().getDbValue());
        claims.put("tenantId", tenant != null ? tenant.getId() : -1);
        if (tenant != null) {
            // Informational for the frontend (e.g. showing a renew banner). The server never
            // trusts these — access is re-checked against the database on each request.
            claims.put("plan", subscriptionService.currentPlan(tenant).getCode());
            claims.put("subscriptionStatus", subscriptionService.currentStatus(tenant).getDbValue());
        }
        String token = jwtService.generateAccessToken(user.getEmail(), claims);

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
        // No legal-name field at signup yet; the address was being stored here by mistake.
        tenant.setLegalName(request.getOrganizationName());
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

    public void resendVerification(String email) {
        emailVerificationTokenService.resend(email);
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Don't reveal whether the email is registered.
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // Silently cap reset mails per account so the endpoint can't be used to flood an inbox.
        if (passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), now.minusMinutes(THROTTLE_WINDOW_MINUTES))
                >= PASSWORD_RESET_MAX_REQUESTS) {
            return;
        }

        // Only the newest link should work — expire any earlier ones still outstanding.
        passwordResetTokenRepository.findByUserIdAndUsedAtIsNullAndExpiresAtAfter(user.getId(), now)
                .forEach(old -> old.setExpiresAt(now));

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(now.plusHours(PASSWORD_RESET_EXPIRY_HOURS));
        passwordResetTokenRepository.save(token);

        log.info("Password reset requested for userId={} (link expires in {}h)", user.getId(), PASSWORD_RESET_EXPIRY_HOURS);
        sendResetAfterCommit(user.getEmail(), user.getFullName(), token.getToken());
    }

    private void sendResetAfterCommit(String email, String fullName, String rawToken) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            mailService.sendPasswordResetEmail(email, fullName, rawToken);
            return;
        }
        // Don't email a link for a token that might still be rolled back.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mailService.sendPasswordResetEmail(email, fullName, rawToken);
            }
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new InvalidTokenException("Invalid reset token"));

        if (token.getUsedAt() != null) {
            throw new InvalidTokenException("This reset link has already been used");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("This reset link has expired");
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
