package com.company.crm.ai.privacy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SEC-5: the last check before text leaves for the AI provider — email addresses, phone and other
 * long identity/account numbers (Aadhaar, card, bank account), PAN and GSTIN, and the query part of
 * links (which can carry access tokens) are replaced by placeholders. Dates and money amounts stay.
 */
public final class AiRedactor {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern URL_QUERY = Pattern.compile("(https?://[^\\s?#]+)[?#]\\S*");
    private static final Pattern GSTIN = Pattern.compile("\\b\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]\\b");
    private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b");
    /** A run of digits with phone-style separators; replaced when it holds 9+ digits and isn't an amount. */
    private static final Pattern NUMBER = Pattern.compile("\\+?\\d[\\d\\s().-]{6,}\\d");
    private static final int MIN_DIGITS = 9;

    private AiRedactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = EMAIL.matcher(text).replaceAll("[email]");
        out = URL_QUERY.matcher(out).replaceAll("$1");
        out = GSTIN.matcher(out).replaceAll("[tax id]");
        out = PAN.matcher(out).replaceAll("[tax id]");
        Matcher m = NUMBER.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String candidate = m.group();
            long digits = candidate.chars().filter(Character::isDigit).count();
            boolean amount = candidate.contains(".") && candidate.matches(".*\\.\\d{1,2}$");
            m.appendReplacement(sb, digits >= MIN_DIGITS && !amount ? "[number]" : Matcher.quoteReplacement(candidate));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
