package com.company.crm.auth.service;

import com.company.crm.edition.EditionService;
import java.util.List;
import com.company.crm.auth.mfa.MfaService;
import com.company.crm.auth.password.PasswordPolicy;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.crypto.DataEncryption;
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
    private final AuditService auditService;
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
    private final MfaService mfaService;
    private final RefreshTokenService refreshTokenService;
    private final EditionService editionService;
    private final PasswordPolicy passwordPolicy;

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

        // SEC-1c: a second step for users with two-step sign-in (or whose role requires it).
        if (mfaService.applies(user)) {
            return AuthResponse.secondStep(!user.isMfaEnabled(), mfaService.startChallenge(user));
        }
        return signIn(user, request.getEmail());
    }

    /** SEC-1c: the code (or a recovery code) after the password. Wrong codes count towards the sign-in lockout. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verifyMfa(String mfaToken, String code) {
        User user = mfaService.challengeUser(mfaToken, MfaService.VERIFY);
        throttle(user.getEmail());
        if (!mfaService.checkCode(user, code)) {
            mfaService.countFailure(mfaToken);
            recordAttempt(user, user.getEmail(), false, "invalid_mfa_code");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "That code isn't right", "MFA_INVALID_CODE");
        }
        mfaService.finish(mfaToken);
        return signIn(user, user.getEmail());
    }

    /** SEC-1c: first sign-in of a role that requires two-step sign-in — the secret for the authenticator app. */
    @Transactional
    public MfaService.Setup startMfaSetup(String mfaToken) {
        return mfaService.beginSetup(mfaService.challengeUser(mfaToken, MfaService.SETUP));
    }

    /** SEC-1c: confirms the new authenticator with a code and finishes signing in (with the recovery codes, once). */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse confirmMfaSetup(String mfaToken, String code) {
        User user = mfaService.challengeUser(mfaToken, MfaService.SETUP);
        throttle(user.getEmail());
        List<String> recoveryCodes;
        try {
            recoveryCodes = mfaService.confirmSetup(user, code);
        } catch (ApiException e) {
            mfaService.countFailure(mfaToken);
            recordAttempt(user, user.getEmail(), false, "invalid_mfa_code");
            throw e;
        }
        mfaService.finish(mfaToken);
        return signIn(user, user.getEmail()).withRecoveryCodes(recoveryCodes);
    }

    private void throttle(String email) {
        long recentFailures = loginAttemptRepository.countByEmailAttemptedAndSuccessfulFalseAndAttemptedAtAfter(
                email, LocalDateTime.now().minusMinutes(THROTTLE_WINDOW_MINUTES));
        if (recentFailures >= LOGIN_MAX_FAILURES) {
            throw ApiException.tooManyRequests("Too many failed sign-in attempts. Try again in "
                    + THROTTLE_WINDOW_MINUTES + " minutes.");
        }
    }

    /** The last step of every sign-in: record it and issue the access and refresh tokens. */
    private AuthResponse signIn(User user, String emailAttempted) {
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, statusMessage(user.getStatus()), null);
        }
        recordAttempt(user, emailAttempted, true, null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        return authMapper.toAuthResponse(accessToken(user), refreshTokenService.issue(user, null), user);
    }

    /**
     * API-G01: a new access token (and a new refresh token, the old one is used up) without signing
     * in again — while the account and its company are still active.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken, null);
        User user = rotation.user();
        if (user.getStatus() != AccountStatus.ACTIVE
                || (user.getTenant() != null && user.getTenant().getStatus() != AccountStatus.ACTIVE)) {
            refreshTokenService.revokeAll(user.getId(), "inactive");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Your account is no longer active", "REFRESH_TOKEN_INVALID");
        }
        return authMapper.toAuthResponse(accessToken(user), rotation.refreshToken(), user);
    }

    /** Signing out ends this device's refresh token (the short-lived access token simply expires). */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private String accessToken(User user) {
        Tenant tenant = user.getTenant();
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
        return jwtService.generateAccessToken(user.getEmail(), claims);
    }

    @Transactional
    public UserSummaryResDto signupAdmin(AdminSignupRequest request) {
        if (request.getRole() != null && !"admin".equals(request.getRole())) {
            throw ApiException.badRequest("Public signup can only create a tenant admin account");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw ApiException.conflict("A user with this email already exists");
        }
        passwordPolicy.check(request.getPassword(), request.getEmail(), request.getName(), null); // SEC-1b

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
        passwordPolicy.remember(user.getId(), user.getPasswordHash());
        auditService.recordSystem(tenant.getId(), AuditAction.TENANT_SIGNED_UP, "user", user.getId(),
                "organization=" + tenant.getCompanyName() + ", admin=" + user.getFullName());

        // BIZ-2: start with the chosen industry edition's fields and pipeline stages.
        if (request.getEdition() != null && !"standard".equals(request.getEdition())) {
            editionService.apply(user, request.getEdition());
        }

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

        // NFR-7.2b: only the token's hash is stored; the raw value exists only in the emailed link.
        String rawResetToken = UUID.randomUUID().toString();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(DataEncryption.sha256(rawResetToken));
        token.setExpiresAt(now.plusHours(PASSWORD_RESET_EXPIRY_HOURS));
        passwordResetTokenRepository.save(token);

        log.info("Password reset requested for userId={} (link expires in {}h)", user.getId(), PASSWORD_RESET_EXPIRY_HOURS);
        sendResetAfterCommit(user.getEmail(), user.getFullName(), rawResetToken);
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
        PasswordResetToken token = passwordResetTokenRepository.findByToken(DataEncryption.sha256(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid reset token"));

        if (token.getUsedAt() != null) {
            throw new InvalidTokenException("This reset link has already been used");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("This reset link has expired");
        }

        User user = token.getUser();
        // SEC-1b: checked first, so a refused password leaves the link usable.
        passwordPolicy.check(newPassword, user.getEmail(), user.getFullName(), user.getId());

        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        passwordPolicy.remember(user.getId(), user.getPasswordHash());
        // API-G01: a new password ends every signed-in session.
        refreshTokenService.revokeAll(user.getId(), "password_reset");
        auditService.recordSystem(user.getTenant() != null ? user.getTenant().getId() : null, AuditAction.PASSWORD_RESET, "user", user.getId(),
                "password reset via emailed link");
    }

    private void recordAttempt(User user, String emailAttempted, boolean successful, String failureReason) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUser(user);
        attempt.setEmailAttempted(emailAttempted);
        attempt.setSuccessful(successful);
        attempt.setFailureReason(failureReason);
        loginAttemptRepository.save(attempt);
        // SEC-7: in the audit trail too (login_attempts also drives the lockout). Never the email typed.
        AuditAction action = successful ? AuditAction.USER_SIGNED_IN : AuditAction.USER_SIGN_IN_FAILED;
        if (user != null) {
            auditService.record(user, action, "user", user.getId(), failureReason);
        } else {
            auditService.recordSystem(null, action, "user", null, failureReason);
        }
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
