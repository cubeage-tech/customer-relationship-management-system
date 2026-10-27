package com.company.crm.auth.service;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.common.exception.ApiException;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * API-G01: long-lived refresh tokens that trade for a new access token at /auth/refresh. Only the
 * SHA-256 of a token is stored. Every refresh rotates: the old token is revoked and a new one of the
 * same family issued. A revoked token presented again (it was copied) revokes the whole family, so
 * the thief and the user both have to sign in again. Signing out, a password reset or an admin
 * resetting two-step sign-in revokes them too.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Value("${app.jwt.refresh-token-days:14}")
    private int ttlDays;

    public record Rotation(User user, String refreshToken) {}

    /** A new token (and family) for a completed sign-in. */
    @Transactional
    public String issue(User user, String deviceInfo) {
        return issue(user, UUID.randomUUID().toString(), deviceInfo);
    }

    /** Trades a refresh token for a new one; 401 when it is unknown, expired or already used. */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken, String deviceInfo) {
        Map<String, Object> row = rawToken == null ? null : jdbc.queryForList(
                "SELECT id, user_id, family, expires_at, revoked_at FROM refresh_tokens WHERE token = ?", DataEncryption.sha256(rawToken))
                .stream().findFirst().orElse(null);
        if (row == null) {
            throw invalid("Your session has ended. Sign in again.");
        }
        long userId = ((Number) row.get("user_id")).longValue();
        String family = (String) row.get("family");
        if (row.get("revoked_at") != null) {
            // Reuse of a rotated token: someone else has a copy. End every session of this family.
            int revoked = jdbc.update("UPDATE refresh_tokens SET revoked_at = now(), revoked_reason = 'reuse' WHERE family = ? AND revoked_at IS NULL", family);
            userRepository.findById(userId).ifPresent(u -> auditService.record(u, AuditAction.REFRESH_TOKEN_REUSED, "user", u.getId(),
                    "a used refresh token was presented again; " + revoked + " session(s) ended"));
            log.warn("Refresh token reuse for user {} — family revoked", userId);
            throw invalid("Your session has ended. Sign in again.");
        }
        if (((Timestamp) row.get("expires_at")).toLocalDateTime().isBefore(LocalDateTime.now())) {
            throw invalid("Your session has expired. Sign in again.");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> invalid("Your session has ended. Sign in again."));
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now(), revoked_reason = 'rotated' WHERE id = ?", ((Number) row.get("id")).longValue());
        return new Rotation(user, issue(user, family, deviceInfo));
    }

    /** Sign-out on this device. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            jdbc.update("UPDATE refresh_tokens SET revoked_at = now(), revoked_reason = 'logout' WHERE token = ? AND revoked_at IS NULL",
                    DataEncryption.sha256(rawToken));
        }
    }

    /** Ends every session of the user (password reset, two-step sign-in reset, account suspended). */
    @Transactional
    public int revokeAll(Long userId, String reason) {
        return jdbc.update("UPDATE refresh_tokens SET revoked_at = now(), revoked_reason = ? WHERE user_id = ? AND revoked_at IS NULL", reason, userId);
    }

    private String issue(User user, String family, String deviceInfo) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO refresh_tokens (user_id, token, family, device_info, expires_at) VALUES (?, ?, ?, ?, ?)",
                user.getId(), DataEncryption.sha256(raw), family, truncate(deviceInfo),
                Timestamp.valueOf(LocalDateTime.now().plusDays(ttlDays)));
        return raw;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message, "REFRESH_TOKEN_INVALID");
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 255 ? value : value.substring(0, 255);
    }
}
