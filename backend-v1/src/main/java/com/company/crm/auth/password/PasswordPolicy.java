package com.company.crm.auth.password;

import com.company.crm.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SEC-1b: what a new password must be — at least 10 characters (configurable) mixing three kinds
 * of character; not a well-known password; not the user's name or email; not found in known data
 * breaches (when the breach check is on); and not one of the user's last 5 passwords.
 */
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    public static final int MAX_LENGTH = 128;
    private static final int MIN_KINDS = 3;
    private static final int MIN_DISTINCT = 5;

    private static final Set<String> COMMON = loadCommon();

    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final BreachedPasswordChecker breachedPasswords;

    @Value("${app.security.password.min-length:10}")
    private int minLength = 10;

    @Value("${app.security.password.history:5}")
    private int historySize = 5;

    /** The rules, for the sign-up and reset forms. */
    public record Rules(int minLength, int maxLength, int minCharacterKinds, boolean breachCheck, int history) {}

    public Rules rules() {
        return new Rules(minLength, MAX_LENGTH, MIN_KINDS, breachedPasswords.isEnabled(), historySize);
    }

    /**
     * Throws 400 WEAK_PASSWORD naming the first rule the password breaks. {@code userId} is null for
     * a new account (nothing to compare with).
     */
    public void check(String password, String email, String fullName, Long userId) {
        String problem = problem(password, email, fullName);
        if (problem == null && userId != null && reused(userId, password)) {
            problem = "Choose a password you haven't used recently";
        }
        if (problem == null && breachedPasswords.isBreached(password)) {
            problem = "This password has appeared in a data breach. Choose a different one";
        }
        if (problem != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, problem, "WEAK_PASSWORD");
        }
    }

    /** Keeps the new password's hash for the reuse check, and only the last {@code history} of them. */
    public void remember(Long userId, String passwordHash) {
        jdbc.update("INSERT INTO password_history (user_id, password_hash) VALUES (?, ?)", userId, passwordHash);
        jdbc.update("DELETE FROM password_history WHERE user_id = ? AND id NOT IN "
                + "(SELECT id FROM password_history WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?)", userId, userId, historySize);
    }

    /** The rules that need no lookup; null when the password passes them. */
    String problem(String password, String email, String fullName) {
        if (password == null || password.isBlank()) {
            return "Enter a password";
        }
        if (password.length() < minLength) {
            return "Use at least " + minLength + " characters";
        }
        if (password.length() > MAX_LENGTH) {
            return "Use at most " + MAX_LENGTH + " characters";
        }
        int kinds = (password.chars().anyMatch(Character::isLowerCase) ? 1 : 0)
                + (password.chars().anyMatch(Character::isUpperCase) ? 1 : 0)
                + (password.chars().anyMatch(Character::isDigit) ? 1 : 0)
                + (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)) ? 1 : 0);
        if (kinds < MIN_KINDS) {
            return "Mix at least three of: lowercase letters, capitals, numbers and symbols";
        }
        if (password.chars().distinct().count() < MIN_DISTINCT) {
            return "Use more different characters";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            return "This password is too common. Choose a different one";
        }
        for (String part : personalParts(email, fullName)) {
            if (lower.contains(part)) {
                return "Don't use your name or email address in the password";
            }
        }
        return null;
    }

    private boolean reused(Long userId, String password) {
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM users WHERE id = ? UNION ALL "
                + "(SELECT password_hash FROM password_history WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?)",
                String.class, userId, userId, historySize);
        return hashes.stream().anyMatch(hash -> hash != null && passwordEncoder.matches(password, hash));
    }

    /** The name's words and the email's name part, 4+ characters each. */
    private static Set<String> personalParts(String email, String fullName) {
        Set<String> parts = new HashSet<>();
        if (email != null && email.contains("@")) {
            String local = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            parts.add(local);
            parts.addAll(List.of(local.split("[^a-z0-9]+")));
        }
        if (fullName != null) {
            parts.addAll(List.of(fullName.toLowerCase(Locale.ROOT).split("[^\\p{L}0-9]+")));
        }
        parts.removeIf(p -> p.length() < 4);
        return parts;
    }

    private static Set<String> loadCommon() {
        Set<String> common = new HashSet<>();
        try (InputStream in = PasswordPolicy.class.getResourceAsStream("/security/common-passwords.txt")) {
            if (in == null) {
                return common;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String entry = line.trim().toLowerCase(Locale.ROOT);
                if (!entry.isEmpty() && !entry.startsWith("#")) {
                    common.add(entry);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return common;
    }
}
