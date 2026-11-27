package com.company.crm.common.validation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Centralized validation utilities for SmartCRM AI backend (Spring Boot).
 * Mirrors the frontend's validation.js so both layers enforce identical rules —
 * client-side checks are UX only; these are the actual security boundary.
 *
 * Each validate*() method returns an error message String on failure, or null on success.
 * Compose these in service-layer validate(...) methods, or wrap them in a custom
 * jakarta.validation.ConstraintValidator if you want them usable as DTO annotations.
 */
public final class Validation {

    private Validation() {
        // static utility class — no instances
    }

    // -----------------------------------------------------------------------
    // Regex patterns
    // -----------------------------------------------------------------------

    public static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    public static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z\\s'-]{1,49}$");
    // India-style bank account number: 9 to 18 digits (covers most Indian banks)
    public static final Pattern BANK_ACCOUNT_NUMBER = Pattern.compile("^\\d{9,18}$");
    public static final Pattern IFSC_CODE = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    public static final Pattern PHONE_INDIA = Pattern.compile("^[6-9]\\d{9}$");
    public static final Pattern PAN = Pattern.compile("^[A-Z]{5}\\d{4}[A-Z]$");
    public static final Pattern GSTIN = Pattern.compile("^\\d{2}[A-Z]{5}\\d{4}[A-Z]\\d[A-Z]\\d[A-Z]$");
    public static final Pattern ORG_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9\\s.,'&-]{1,99}$");
    public static final Pattern URL = Pattern.compile("^(https?://)?([\\w-]+\\.)+[\\w-]{2,}(/\\S*)?$");
    public static final Pattern ALPHANUMERIC = Pattern.compile("^[A-Za-z0-9]+$");

    /**
     * Known disposable/temp email domains worth blocking at signup.
     * Extend this over time — consider moving to config or a DB table if it grows large.
     */
    public static final Set<String> DISPOSABLE_EMAIL_DOMAINS = Set.of(
            "mailinator.com",
            "guerrillamail.com",
            "tempmail.com",
            "temp-mail.org",
            "10minutemail.com",
            "throwawaymail.com",
            "yopmail.com",
            "fakeinbox.com",
            "trashmail.com"
    );

    // -----------------------------------------------------------------------
    // Generic helpers
    // -----------------------------------------------------------------------

    public static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String required(String value, String fieldLabel) {
        if (isEmpty(value)) {
            return fieldLabel + " is required.";
        }
        return null;
    }

    public static String minLength(String value, int min, String fieldLabel) {
        if (value == null || value.length() < min) {
            return fieldLabel + " must be at least " + min + " characters.";
        }
        return null;
    }

    public static String maxLength(String value, int max, String fieldLabel) {
        if (value != null && value.length() > max) {
            return fieldLabel + " must be at most " + max + " characters.";
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Name fields (first name / last name)
    // -----------------------------------------------------------------------

    public static String validateName(String value, String fieldLabel) {
        String err = required(value, fieldLabel);
        if (err != null) return err;

        if (!NAME.matcher(value.trim()).matches()) {
            return fieldLabel + " must be 2-50 letters and may include spaces, hyphens, or apostrophes.";
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Email
    // -----------------------------------------------------------------------

    public static String validateEmail(String value) {
        String err = required(value, "Email");
        if (err != null) return err;

        String trimmed = value.trim();
        if (!EMAIL.matcher(trimmed).matches()) {
            return "Enter a valid email address.";
        }

        String domain = extractDomain(trimmed);
        if (domain != null && DISPOSABLE_EMAIL_DOMAINS.contains(domain.toLowerCase())) {
            return "Disposable email addresses are not allowed. Please use a permanent email.";
        }

        return null;
    }

    private static String extractDomain(String email) {
        int at = email.indexOf('@');
        if (at < 0 || at == email.length() - 1) return null;
        return email.substring(at + 1);
    }

    /**
     * Normalizes an email for uniqueness comparison — strips Gmail-style +aliases
     * and dots so john+1@gmail.com and j.ohn@gmail.com resolve to the same identity.
     * Use this before persisting / checking uniqueness in the DB. This is the
     * authoritative check — never rely solely on the frontend's copy of this logic.
     */
    public static String normalizeEmail(String value) {
        if (isEmpty(value)) return "";

        String lower = value.trim().toLowerCase();
        int at = lower.indexOf('@');
        if (at < 0) return lower;

        String local = lower.substring(0, at);
        String domain = lower.substring(at + 1);

        int plus = local.indexOf('+');
        if (plus >= 0) {
            local = local.substring(0, plus);
        }

        if (domain.equals("gmail.com") || domain.equals("googlemail.com")) {
            local = local.replace(".", "");
            domain = "gmail.com";
        }

        return local + "@" + domain;
    }

    // -----------------------------------------------------------------------
    // Organization / company name
    // -----------------------------------------------------------------------

    public static String validateOrganizationName(String value) {
        String err = required(value, "Company name");
        if (err != null) return err;

        if (!ORG_NAME.matcher(value.trim()).matches()) {
            return "Company name contains invalid characters.";
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Bank account number
    // -----------------------------------------------------------------------

    public static String validateBankAccountNumber(String value) {
        String err = required(value, "Bank account number");
        if (err != null) return err;

        if (!BANK_ACCOUNT_NUMBER.matcher(value.trim()).matches()) {
            return "Enter a valid bank account number (9-18 digits, numbers only).";
        }
        return null;
    }

    public static String validateIfscCode(String value) {
        String err = required(value, "IFSC code");
        if (err != null) return err;

        if (!IFSC_CODE.matcher(value.trim().toUpperCase()).matches()) {
            return "Enter a valid IFSC code (e.g. HDFC0001234).";
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Phone (India)
    // -----------------------------------------------------------------------

    public static String validatePhoneIndia(String value) {
        String err = required(value, "Phone number");
        if (err != null) return err;

        if (!PHONE_INDIA.matcher(value.trim()).matches()) {
            return "Enter a valid 10-digit Indian mobile number.";
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // PAN / GSTIN (India business identifiers)
    // -----------------------------------------------------------------------

    public static String validatePan(String value) {
        String err = required(value, "PAN");
        if (err != null) return err;

        if (!PAN.matcher(value.trim().toUpperCase()).matches()) {
            return "Enter a valid PAN (e.g. ABCDE1234F).";
        }
        return null;
    }

    public static String validateGstin(String value) {
        String err = required(value, "GSTIN");
        if (err != null) return err;

        if (!GSTIN.matcher(value.trim().toUpperCase()).matches()) {
            return "Enter a valid GSTIN.";
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Password
    // -----------------------------------------------------------------------

    /** Simple holder for password strength score + label, mirrors the frontend meter. */
    public static final class PasswordStrength {
        public final int score; // 0-4
        public final String label;

        public PasswordStrength(int score, String label) {
            this.score = score;
            this.label = label;
        }
    }

    public static PasswordStrength getPasswordStrength(String password) {
        if (isEmpty(password)) {
            return new PasswordStrength(0, "Too short");
        }

        int score = 0;
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;
        if (password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)) score++;
        if (password.chars().anyMatch(Character::isDigit)) score++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) score++;

        score = Math.min(score, 4);

        String[] labels = {"Too short", "Weak", "Fair", "Good", "Strong"};
        String label = password.length() < 8 ? "Too short" : labels[score];
        return new PasswordStrength(score, label);
    }

    public static String validatePassword(String value) {
        return validatePassword(value, 2);
    }

    public static String validatePassword(String value, int minScore) {
        String err = required(value, "Password");
        if (err != null) return err;

        String lengthErr = minLength(value, 8, "Password");
        if (lengthErr != null) return lengthErr;

        PasswordStrength strength = getPasswordStrength(value);
        if (strength.score < minScore) {
            return "Password is too weak (" + strength.label
                    + "). Use a longer password with a mix of letters, numbers, and symbols.";
        }

        return null;
    }

    public static String validateConfirmPassword(String password, String confirmPassword) {
        String err = required(confirmPassword, "Confirm password");
        if (err != null) return err;

        if (!password.equals(confirmPassword)) {
            return "Passwords do not match.";
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Terms acceptance
    // -----------------------------------------------------------------------

    public static String validateTermsAccepted(Boolean accepted) {
        if (accepted == null || !accepted) {
            return "You must accept the Terms of Service to continue.";
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // URL
    // -----------------------------------------------------------------------

    public static String validateUrl(String value, String fieldLabel) {
        String err = required(value, fieldLabel);
        if (err != null) return err;

        if (!URL.matcher(value.trim()).matches()) {
            return "Enter a valid " + fieldLabel.toLowerCase() + ".";
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Composite: validate an entire signup payload in one call.
    // Returns a map keyed by field name; empty map means no errors.
    // Adjust the record/DTO type below to match your actual SignupRequest.
    // -----------------------------------------------------------------------

    public static Map<String, String> validateSignupForm(
            String firstName,
            String lastName,
            String email,
            String organizationName,
            String bankAccountNumber,
            String password,
            String confirmPassword,
            Boolean termsAccepted
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        putIfPresent(errors, "firstName", validateName(firstName, "First name"));
        putIfPresent(errors, "lastName", validateName(lastName, "Last name"));
        putIfPresent(errors, "email", validateEmail(email));
        putIfPresent(errors, "organizationName", validateOrganizationName(organizationName));
        putIfPresent(errors, "bankAccountNumber", validateBankAccountNumber(bankAccountNumber));
        putIfPresent(errors, "password", validatePassword(password));

        if (password != null) {
            putIfPresent(errors, "confirmPassword", validateConfirmPassword(password, confirmPassword));
        }

        putIfPresent(errors, "termsAccepted", validateTermsAccepted(termsAccepted));

        return errors;
    }

    private static void putIfPresent(Map<String, String> errors, String field, String message) {
        if (message != null) {
            errors.put(field, message);
        }
    }
}