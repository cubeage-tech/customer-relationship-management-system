package com.company.crm.communication.service;

import com.company.crm.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Normalises phone numbers to E.164 (+919876543210) for sending and for matching replies. */
@Component
public class PhoneNumbers {

    /** Applied to bare 10-digit numbers (SRS 2.6: India is the primary market). */
    @Value("${app.communication.default-country-code:+91}")
    private String defaultCountryCode;

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
        } else {
            e164 = "+" + digits;
        }
        int length = e164.length() - 1;
        return length >= 8 && length <= 15 ? e164 : null;
    }
}
