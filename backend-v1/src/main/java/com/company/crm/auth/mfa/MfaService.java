package com.company.crm.auth.mfa;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SEC-1c: two-step sign-in with an authenticator app. Required for the roles in
 * MFA_REQUIRED_ROLES (default: super admin and tenant admin), optional for everyone else. After the
 * password, the sign-in continues with a short-lived challenge: enter a code (or a one-time
 * recovery code), or — first time, for a role that requires it — set the app up. An admin can
 * reset a user who lost their phone.
 */
@Service
@RequiredArgsConstructor
public class MfaService {

    public static final String VERIFY = "verify";
    public static final String SETUP = "setup";
    private static final int CHALLENGE_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RECOVERY_CODES = 8;
    private static final String CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    @Value("${app.security.mfa.required-roles:super_admin,admin}")
    private String requiredRoles;

    @Value("${app.security.mfa.issuer:SmartCRM}")
    private String issuer;

    public record Setup(String secret, String otpauthUri) {}

    public record Status(boolean enabled, boolean required, int recoveryCodesLeft, LocalDateTime enabledAt) {}

    // ==================== policy ====================

    public boolean requiredFor(User user) {
        Set<String> roles = Arrays.stream(requiredRoles.split(",")).map(String::trim).filter(r -> !r.isEmpty()).collect(Collectors.toSet());
        return user.getRole() != null && roles.contains(user.getRole().getName().getDbValue());
    }

    /** Whether signing in needs a second step (enabled, or required for the role and still to be set up). */
    public boolean applies(User user) {
        return user.isMfaEnabled() || requiredFor(user);
    }

    // ==================== sign-in challenges ====================

    /** Starts the second step after a correct password; returns the token the client sends back. */
    @Transactional
    public String startChallenge(User user) {
        jdbc.update("DELETE FROM mfa_challenges WHERE user_id = ? OR expires_at < now()", user.getId());
        String token = randomToken();
        jdbc.update("INSERT INTO mfa_challenges (token_hash, user_id, purpose, expires_at) VALUES (?, ?, ?, ?)",
                DataEncryption.sha256(token), user.getId(), user.isMfaEnabled() ? VERIFY : SETUP,
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(CHALLENGE_MINUTES)));
        return token;
    }

    /** The user a live challenge belongs to; 401 when it is unknown, expired, used up or for the other step. */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public User challengeUser(String token, String purpose) {
        Map<String, Object> row = token == null ? null : jdbc.queryForList(
                "SELECT user_id, purpose, attempts, expires_at FROM mfa_challenges WHERE token_hash = ?", DataEncryption.sha256(token))
                .stream().findFirst().orElse(null);
        if (row == null || ((Timestamp) row.get("expires_at")).toLocalDateTime().isBefore(LocalDateTime.now())
                || ((Number) row.get("attempts")).intValue() >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "This sign-in has expired. Sign in again.", "MFA_CHALLENGE_EXPIRED");
        }
        if (!purpose.equals(row.get("purpose"))) {
            throw ApiException.badRequest("Wrong sign-in step");
        }
        return userRepository.findById(((Number) row.get("user_id")).longValue())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "This sign-in has expired. Sign in again.", "MFA_CHALLENGE_EXPIRED"));
    }

    @Transactional
    public void countFailure(String token) {
        jdbc.update("UPDATE mfa_challenges SET attempts = attempts + 1 WHERE token_hash = ?", DataEncryption.sha256(token));
    }

    @Transactional
    public void finish(String token) {
        jdbc.update("DELETE FROM mfa_challenges WHERE token_hash = ?", DataEncryption.sha256(token));
    }

    // ==================== codes ====================

    /** An authenticator code (or an unused recovery code) for an enrolled user; consumes what it used. */
    @Transactional
    public boolean checkCode(User user, String code) {
        if (!user.isMfaEnabled() || code == null) {
            return false;
        }
        String clean = code.replaceAll("\\s", "");
        OptionalLong step = Totp.verify(user.getMfaSecret(), clean, user.getMfaLastStep(), Instant.now());
        if (step.isPresent()) {
            user.setMfaLastStep(step.getAsLong());
            userRepository.save(user);
            return true;
        }
        String normalized = clean.toLowerCase(Locale.ROOT).replace("-", "");
        if (normalized.length() == 8 && jdbc.update("UPDATE mfa_recovery_codes SET used_at = now() WHERE id = (SELECT id FROM mfa_recovery_codes"
                + " WHERE user_id = ? AND code_hash = ? AND used_at IS NULL LIMIT 1)", user.getId(), DataEncryption.sha256(normalized)) == 1) {
            auditService.record(user, AuditAction.MFA_RECOVERY_CODE_USED, "user", user.getId(), recoveryLeft(user) + " recovery code(s) left");
            return true;
        }
        return false;
    }

    // ==================== setup ====================

    /** A new secret to add to the authenticator app; becomes active once confirmed with a code. */
    @Transactional
    public Setup beginSetup(User user) {
        String secret = Totp.newSecret();
        user.setMfaPendingSecret(secret);
        userRepository.save(user);
        return new Setup(secret, Totp.otpauthUri(issuer, user.getEmail(), secret));
    }

    /** Confirms the pending secret with a code from the app: two-step sign-in is on. Returns new recovery codes. */
    @Transactional(noRollbackFor = ApiException.class)
    public List<String> confirmSetup(User user, String code) {
        OptionalLong step = Totp.verify(user.getMfaPendingSecret(), code == null ? null : code.replaceAll("\\s", ""), null, Instant.now());
        if (step.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That code doesn't match. Check the time on your phone and try the newest code.", "MFA_INVALID_CODE");
        }
        user.setMfaSecret(user.getMfaPendingSecret());
        user.setMfaPendingSecret(null);
        user.setMfaLastStep(step.getAsLong());
        user.setMfaEnabled(true);
        user.setMfaEnabledAt(LocalDateTime.now());
        userRepository.save(user);
        auditService.record(user, AuditAction.MFA_ENABLED, "user", user.getId(), null);
        return newRecoveryCodes(user);
    }

    // ==================== self-service ====================

    @Transactional(readOnly = true)
    public Status status(User user) {
        User u = reload(user);
        return new Status(u.isMfaEnabled(), requiredFor(u), u.isMfaEnabled() ? recoveryLeft(u) : 0, u.getMfaEnabledAt());
    }

    @Transactional
    public Setup beginSetupForSelf(User user) {
        User u = reload(user);
        if (u.isMfaEnabled()) {
            throw ApiException.conflict("Two-step sign-in is already on");
        }
        return beginSetup(u);
    }

    @Transactional
    public List<String> confirmSetupForSelf(User user, String code) {
        return confirmSetup(reload(user), code);
    }

    @Transactional
    public void disable(User user, String code) {
        User u = reload(user);
        if (requiredFor(u)) {
            throw ApiException.badRequest("Two-step sign-in is required for your role");
        }
        requireCode(u, code);
        clear(u);
        auditService.record(u, AuditAction.MFA_DISABLED, "user", u.getId(), null);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(User user, String code) {
        User u = reload(user);
        requireCode(u, code);
        auditService.record(u, AuditAction.MFA_RECOVERY_CODES_REGENERATED, "user", u.getId(), null);
        return newRecoveryCodes(u);
    }

    /**
     * Someone lost their phone: a tenant admin resets a user of their tenant, a platform admin any
     * user. Their next sign-in sets it up again (if their role requires it).
     */
    @Transactional
    public void reset(User admin, Long userId) {
        User target = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        boolean platform = admin.getRole().getName() == RoleType.SUPER_ADMIN;
        boolean sameTenant = admin.getTenant() != null && target.getTenant() != null && admin.getTenant().getId().equals(target.getTenant().getId());
        if (!platform && !sameTenant) {
            throw ApiException.notFound("User not found");
        }
        if (admin.getId().equals(target.getId())) {
            throw ApiException.badRequest("Use your own two-step sign-in settings to change it");
        }
        clear(target);
        // API-G01: the lost phone may still be signed in — end every session.
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now(), revoked_reason = 'mfa_reset' WHERE user_id = ? AND revoked_at IS NULL", target.getId());
        auditService.record(admin, target.getTenant() != null ? target.getTenant().getId() : null, AuditAction.MFA_RESET, "user", target.getId(),
                "two-step sign-in reset");
    }

    // ==================== helpers ====================

    private void requireCode(User u, String code) {
        if (!checkCode(u, code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That code isn't right", "MFA_INVALID_CODE");
        }
    }

    private void clear(User u) {
        u.setMfaEnabled(false);
        u.setMfaSecret(null);
        u.setMfaPendingSecret(null);
        u.setMfaLastStep(null);
        u.setMfaEnabledAt(null);
        userRepository.save(u);
        jdbc.update("DELETE FROM mfa_recovery_codes WHERE user_id = ?", u.getId());
        jdbc.update("DELETE FROM mfa_challenges WHERE user_id = ?", u.getId());
    }

    private List<String> newRecoveryCodes(User u) {
        jdbc.update("DELETE FROM mfa_recovery_codes WHERE user_id = ?", u.getId());
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder c = new StringBuilder();
            for (int j = 0; j < 8; j++) {
                c.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            jdbc.update("INSERT INTO mfa_recovery_codes (user_id, code_hash) VALUES (?, ?)", u.getId(), DataEncryption.sha256(c.toString()));
            codes.add(c.substring(0, 4) + "-" + c.substring(4));
        }
        return codes;
    }

    private int recoveryLeft(User u) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM mfa_recovery_codes WHERE user_id = ? AND used_at IS NULL", Integer.class, u.getId());
        return n == null ? 0 : n;
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
