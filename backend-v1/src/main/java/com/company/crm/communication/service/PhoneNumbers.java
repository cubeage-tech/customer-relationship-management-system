package com.company.crm.communication.service;

import com.company.crm.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalises phone numbers to E.164 (+919876543210) for sending and for matching replies.
 * VAL-2: numbers in the configured country (DEFAULT_COUNTRY_CODE) must also fit that country's
 * numbering plan; other countries get the general E.164 length check.
 */
@Component
public class PhoneNumbers {

    /** Applied to bare 10-digit numbers (SRS 2.6: India is the primary market). */
    @Value("${app.communication.default-country-code:+91}")
    private String defaultCountryCode;

    /** National number (after the country code) per country: the shape a real number takes. */
    private static final Map<String, Pattern> NATIONAL = Map.of(
            "+91", Pattern.compile("[1-9]\\d{9}"),                  // India: 10 digits
            "+1", Pattern.compile("[2-9]\\d{2}[2-9]\\d{6}"),        // US / Canada (NANP)
            "+44", Pattern.compile("[1-9]\\d{8,9}"),                // United Kingdom
            "+971", Pattern.compile("[1-9]\\d{7,8}"),               // United Arab Emirates
            "+61", Pattern.compile("[1-9]\\d{8}"),                  // Australia
            "+65", Pattern.compile("[3689]\\d{7}"),                 // Singapore
            "+966", Pattern.compile("[1-9]\\d{7,8}"),               // Saudi Arabia
            "+977", Pattern.compile("[1-9]\\d{7,9}"),               // Nepal
            "+880", Pattern.compile("[1-9]\\d{7,9}"));              // Bangladesh

    public String defaultCountryCode() {
        return defaultCountryCode;
    }

    /** VAL-2: true when {@code raw} is a phone number and, if it is in the configured country, fits its plan. */
    public boolean isValid(String raw) {
        String e164 = normalizeOrNull(raw);
        if (e164 == null) {
            return false;
        }
        Pattern national = NATIONAL.get(defaultCountryCode);
        if (national != null && e164.startsWith(defaultCountryCode)) {
            return national.matcher(e164.substring(defaultCountryCode.length())).matches();
        }
        return true;
    }

    /** Returns E.164, or throws 400 when the input cannot be a phone number. */
    public String toE164(String raw) {
        String normalized = normalizeOrNull(raw);
        if (normalized == null) {
            throw ApiException.badRequest("Enter a valid phone number, e.g. +919876543210");
        }
        return normalized;
    }

    /** Same as {@link #toE164} but returns null instead of throwing (for stored data). */
    public String normalizeOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replaceFirst("^whatsapp:", "");
        String digits = value.replaceAll("\\D", "");
        String e164;
        if (value.startsWith("+")) {
            e164 = "+" + digits;
        } else if (digits.startsWith("00")) {
            e164 = "+" + digits.substring(2);
        } else if (digits.length() == 10) {
            e164 = defaultCountryCode + digits;
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            // a national number written with its trunk prefix, e.g. 09876543210
            e164 = defaultCountryCode + digits.substring(1);
        } else {
            e164 = "+" + digits;
        }
        int length = e164.length() - 1;
        return length >= 8 && length <= 15 ? e164 : null;
    }
}
