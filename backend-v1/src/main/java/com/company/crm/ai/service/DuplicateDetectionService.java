package com.company.crm.ai.service;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * FR-AI-9.1: probable duplicate customers across the tenant. Each pair of active customers that
 * share something — the same email or phone, a near-identical company name (Jaro-Winkler on the
 * name with "Pvt Ltd" and the like removed), the same distinctive name words, the same website or
 * business email domain — is scored by combining those signals; pairs at 50%+ are listed, "likely"
 * from 85%, with the reasons. Candidates are found by blocking keys, so the scan doesn't compare
 * every pair. Worked out inside the CRM (nothing goes to an AI provider). A user sees pairs where
 * they can open both customers, and can mark a pair "not a duplicate" so it isn't suggested again.
 */
@Service
@RequiredArgsConstructor
public class DuplicateDetectionService {

    static final double LIKELY = 0.85;
    static final double SHOWN = 0.5;
    private static final int MAX_PAIRS = 100;
    /** Blocks larger than this (a very common word or prefix) are skipped for name keys. */
    private static final int MAX_BLOCK = 60;

    private static final Pattern SUFFIXES = Pattern.compile(
            "\\b(private|pvt|limited|ltd|llp|llc|inc|incorporated|corp|corporation|co|company|the|and|of)\\b");
    /** Words too common in company names to suggest two are the same. */
    private static final Set<String> COMMON = Set.of("industries", "industry", "enterprises", "enterprise", "solutions", "services", "group",
            "india", "traders", "trading", "technologies", "technology", "tech", "systems", "international", "global", "engineering",
            "engineers", "works", "manufacturing", "products", "exports", "imports", "agency", "associates", "brothers", "sons", "store");
    private static final Set<String> FREE_MAIL = Set.of("gmail.com", "googlemail.com", "yahoo.com", "yahoo.co.in", "yahoo.in", "outlook.com",
            "hotmail.com", "live.com", "msn.com", "icloud.com", "me.com", "aol.com", "rediffmail.com", "protonmail.com", "proton.me", "ymail.com",
            "gmx.com", "mail.com", "zohomail.com", "zohomail.in");

    public record Side(Long id, String name, String ownerName, LocalDateTime createdAt) {}

    /** {@code level}: likely | possible; {@code score} 0–1. */
    public record DuplicatePair(Side first, Side second, double score, String level, List<String> reasons) {}

    private record Row(Long id, String name, Long ownerId, String ownerName, LocalDateTime createdAt, String emailHash, String phoneHash,
                       String emailDomain, String website, String norm, Set<String> tokens) {}

    private final JdbcTemplate jdbc;
    private final DataScopeResolver scopes;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<DuplicatePair> customers(User user) {
        Long tenantId = tenant(user);
        DataScope scope = scopes.resolve(user);
        List<Row> rows = jdbc.query("SELECT c.id, c.company_name, c.owner_id, u.full_name, c.created_at, c.email_hash, c.phone_hash, c.email, c.website "
                        + "FROM customers c LEFT JOIN users u ON u.id = c.owner_id WHERE c.tenant_id = ? AND c.status = 'active' AND c.erased_at IS NULL",
                (rs, i) -> {
                    String name = rs.getString("company_name");
                    String norm = normalize(name);
                    return new Row(rs.getLong("id"), name, (Long) rs.getObject("owner_id"), rs.getString("full_name"),
                            rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("email_hash"), rs.getString("phone_hash"),
                            businessDomain(decrypt(rs.getString("email"))), websiteDomain(rs.getString("website")), norm.replace(" ", ""), tokens(norm));
                }, tenantId).stream().filter(r -> scope.allows(r.ownerId())).toList();

        Set<String> dismissedPairs = new HashSet<>();
        jdbc.query("SELECT first_id, second_id FROM duplicate_dismissals WHERE tenant_id = ? AND record_type = 'customer'",
                rs -> { dismissedPairs.add(rs.getLong(1) + ":" + rs.getLong(2)); }, tenantId);

        // Blocking: only records that share a key are compared.
        Map<String, List<Row>> blocks = new HashMap<>();
        for (Row r : rows) {
            for (String key : keys(r)) {
                blocks.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }
        }
        Map<String, DuplicatePair> pairs = new HashMap<>();
        for (Map.Entry<String, List<Row>> block : blocks.entrySet()) {
            List<Row> members = block.getValue();
            boolean nameKey = block.getKey().startsWith("n:") || block.getKey().startsWith("t:");
            if (members.size() < 2 || (nameKey && members.size() > MAX_BLOCK)) {
                continue;
            }
            for (int i = 0; i < members.size(); i++) {
                for (int j = i + 1; j < members.size(); j++) {
                    Row a = members.get(i).id() < members.get(j).id() ? members.get(i) : members.get(j);
                    Row b = a == members.get(i) ? members.get(j) : members.get(i);
                    String id = a.id() + ":" + b.id();
                    if (pairs.containsKey(id) || dismissedPairs.contains(id)) {
                        continue;
                    }
                    DuplicatePair pair = score(a, b);
                    if (pair != null) {
                        pairs.put(id, pair);
                    }
                }
            }
        }
        return pairs.values().stream()
                .sorted(Comparator.comparingDouble(DuplicatePair::score).reversed().thenComparing(p -> p.first().id()))
                .limit(MAX_PAIRS).toList();
    }

    /** "Not a duplicate": the pair is no longer suggested. */
    @Transactional
    public void dismiss(User user, Long oneId, Long otherId) {
        if (oneId == null || otherId == null || oneId.equals(otherId)) {
            throw ApiException.badRequest("Give two different customers");
        }
        Long tenantId = tenant(user);
        DataScope scope = scopes.resolve(user);
        List<Map<String, Object>> found = jdbc.queryForList("SELECT id, owner_id FROM customers WHERE tenant_id = ? AND id IN (?, ?)", tenantId, oneId, otherId);
        if (found.size() != 2 || found.stream().anyMatch(r -> !scope.allows(r.get("owner_id") == null ? null : ((Number) r.get("owner_id")).longValue()))) {
            throw ApiException.notFound("Customer not found");
        }
        long first = Math.min(oneId, otherId);
        long second = Math.max(oneId, otherId);
        try {
            jdbc.update("INSERT INTO duplicate_dismissals (tenant_id, record_type, first_id, second_id, dismissed_by) VALUES (?, 'customer', ?, ?, ?)",
                    tenantId, first, second, user.getId());
        } catch (DuplicateKeyException alreadyDismissed) {
            return;
        }
        auditService.record(user, AuditAction.DUPLICATE_DISMISSED, "customer", first, "customers #" + first + " and #" + second + " are not duplicates");
    }

    /**
     * Save-time check (VAL-1): active customers whose name is near-identical to {@code companyName}
     * (same first letters, Jaro-Winkler 92%+), most similar first. Exact matches are left to the
     * caller's exact check.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> similarNames(Long tenantId, String companyName, long excludeId) {
        String norm = normalize(companyName).replace(" ", "");
        if (norm.length() < 4) {
            return List.of();
        }
        return jdbc.queryForList("SELECT 'customer' AS type, id, company_name AS name, owner_id FROM customers WHERE tenant_id = ? AND erased_at IS NULL "
                        + "AND id <> ? AND lower(company_name) LIKE ?", tenantId, excludeId, norm.charAt(0) + "%").stream()
                .filter(r -> {
                    String other = normalize((String) r.get("name")).replace(" ", "");
                    return !other.equals(norm) && jaroWinkler(norm, other) >= 0.92;
                })
                .sorted(Comparator.comparingDouble(r -> -jaroWinkler(norm, normalize((String) r.get("name")).replace(" ", ""))))
                .limit(5).toList();
    }

    // ==================== scoring ====================

    static DuplicatePair score(Row a, Row b) {
        List<String> reasons = new ArrayList<>();
        List<Double> signals = new ArrayList<>();
        if (a.emailHash() != null && a.emailHash().equals(b.emailHash())) {
            signals.add(0.95);
            reasons.add("Same email address");
        }
        if (a.phoneHash() != null && a.phoneHash().equals(b.phoneHash())) {
            signals.add(0.8);
            reasons.add("Same phone number");
        }
        double sim = a.norm().isEmpty() || b.norm().isEmpty() ? 0 : jaroWinkler(a.norm(), b.norm());
        if (a.norm().equals(b.norm()) && !a.norm().isEmpty()) {
            signals.add(0.9);
            reasons.add("Same company name (ignoring case, punctuation and \"Pvt Ltd\")");
        } else if (sim >= 0.97) {
            signals.add(0.8);
            reasons.add("Almost the same company name (" + Math.round(sim * 100) + "% alike)");
        } else if (sim >= 0.92) {
            signals.add(0.6);
            reasons.add("Very similar company name (" + Math.round(sim * 100) + "% alike)");
        } else if (sim >= 0.85) {
            signals.add(0.3);
            reasons.add("Similar company name (" + Math.round(sim * 100) + "% alike)");
        }
        Set<String> shared = new LinkedHashSet<>(a.tokens());
        shared.retainAll(b.tokens());
        Set<String> union = new HashSet<>(a.tokens());
        union.addAll(b.tokens());
        if (!shared.isEmpty() && sim < 0.92 && (double) shared.size() / union.size() >= 0.5) {
            signals.add(0.4);
            reasons.add("Same distinctive name words: " + String.join(", ", shared));
        }
        if (a.website() != null && a.website().equals(b.website())) {
            signals.add(0.75);
            reasons.add("Same website (" + a.website() + ")");
        }
        if (a.emailDomain() != null && a.emailDomain().equals(b.emailDomain()) && !reasons.contains("Same email address")) {
            signals.add(0.5);
            reasons.add("Email at the same company domain (" + a.emailDomain() + ")");
        }
        double notSame = 1;
        for (double s : signals) {
            notSame *= 1 - s;
        }
        double score = Math.round((1 - notSame) * 100) / 100.0;
        if (score < SHOWN) {
            return null;
        }
        return new DuplicatePair(side(a), side(b), score, score >= LIKELY ? "likely" : "possible", reasons);
    }

    private static Side side(Row r) {
        return new Side(r.id(), r.name(), r.ownerName(), r.createdAt());
    }

    private static Set<String> keys(Row r) {
        Set<String> keys = new LinkedHashSet<>();
        if (r.emailHash() != null) {
            keys.add("e:" + r.emailHash());
        }
        if (r.phoneHash() != null) {
            keys.add("p:" + r.phoneHash());
        }
        if (r.emailDomain() != null) {
            keys.add("d:" + r.emailDomain());
        }
        if (r.website() != null) {
            keys.add("w:" + r.website());
        }
        if (r.norm().length() >= 3) {
            keys.add("n:" + r.norm().substring(0, Math.min(4, r.norm().length())));
        }
        r.tokens().forEach(t -> keys.add("t:" + t));
        return keys;
    }

    /** Lower case, suffix words removed, letters and digits kept, single spaces. */
    static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String n = name.toLowerCase(Locale.ROOT).replace('&', ' ').replaceAll("[^a-z0-9 ]", " ");
        return SUFFIXES.matcher(n).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private static Set<String> tokens(String normalized) {
        Set<String> out = new LinkedHashSet<>();
        Arrays.stream(normalized.split(" ")).filter(t -> t.length() >= 4 && !COMMON.contains(t)).forEach(out::add);
        return out;
    }

    static String businessDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        String domain = email.substring(email.lastIndexOf('@') + 1).trim().toLowerCase(Locale.ROOT);
        return domain.isEmpty() || FREE_MAIL.contains(domain) ? null : domain;
    }

    static String websiteDomain(String website) {
        if (website == null || website.isBlank()) {
            return null;
        }
        String w = website.trim().toLowerCase(Locale.ROOT).replaceFirst("^[a-z]+://", "").replaceFirst("^www\\.", "");
        int cut = w.indexOf('/');
        w = cut >= 0 ? w.substring(0, cut) : w;
        return w.contains(".") ? w : null;
    }

    /** Jaro-Winkler similarity, 0–1. */
    static double jaroWinkler(String s, String t) {
        if (s.equals(t)) {
            return 1;
        }
        int range = Math.max(0, Math.max(s.length(), t.length()) / 2 - 1);
        boolean[] sm = new boolean[s.length()];
        boolean[] tm = new boolean[t.length()];
        int matches = 0;
        for (int i = 0; i < s.length(); i++) {
            for (int j = Math.max(0, i - range); j < Math.min(t.length(), i + range + 1); j++) {
                if (!tm[j] && s.charAt(i) == t.charAt(j)) {
                    sm[i] = true;
                    tm[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) {
            return 0;
        }
        int transpositions = 0;
        for (int i = 0, k = 0; i < s.length(); i++) {
            if (sm[i]) {
                while (!tm[k]) {
                    k++;
                }
                if (s.charAt(i) != t.charAt(k)) {
                    transpositions++;
                }
                k++;
            }
        }
        double m = matches;
        double jaro = (m / s.length() + m / t.length() + (m - transpositions / 2.0) / m) / 3;
        int prefix = 0;
        while (prefix < Math.min(4, Math.min(s.length(), t.length())) && s.charAt(prefix) == t.charAt(prefix)) {
            prefix++;
        }
        return jaro + prefix * 0.1 * (1 - jaro);
    }

    private static String decrypt(String stored) {
        try {
            return stored == null ? null : DataEncryption.get().decrypt(stored);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Long tenant(User user) {
        if (user.getTenant() == null) {
            throw ApiException.forbidden("Only tenant users can look for duplicates");
        }
        return user.getTenant().getId();
    }
}
