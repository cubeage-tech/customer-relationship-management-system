package com.company.crm.customer.service;

import com.company.crm.ai.service.DuplicateDetectionService;
import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * VAL-1/VAL-3: before a customer, lead or contact is saved, the records of the same tenant that look
 * like the same company or person — the same email (customers, contacts and leads) or the same
 * company name once case, punctuation and suffixes like "Pvt Ltd" are ignored, or (FR-AI-9.1) a
 * near-identical customer name. It is a warning,
 * not a block. A match the user may not open shows only that it exists, not whose it is.
 */
@Service
@RequiredArgsConstructor
public class DuplicateCheckService {

    private static final int MAX_MATCHES = 5;

    /** Company-name words that don't tell two companies apart. Same list as {@link #NAME_SQL}. */
    private static final Pattern SUFFIXES = Pattern.compile(
            "\\b(private|pvt|limited|ltd|llp|llc|inc|incorporated|corp|corporation|co|company|the)\\b");

    /** The same normalisation in SQL: lower case, suffix words removed, then anything but a-z0-9. */
    private static final String NAME_SQL = "regexp_replace(regexp_replace(lower(%s), "
            + "'\\m(private|pvt|limited|ltd|llp|llc|inc|incorporated|corp|corporation|co|company|the)\\M', '', 'g'), '[^a-z0-9]', '', 'g')";

    private final JdbcTemplate jdbc;
    private final DataEncryption encryption;
    private final DataScopeResolver dataScopeResolver;
    private final DuplicateDetectionService detection;

    /** What is being saved: type customer | lead | contact; {@code excludeId} is the record being edited. */
    public record CheckReq(String type, String companyName, String email, Long excludeId) {}

    /** A possible duplicate; {@code recordId} and {@code name} are null when the caller can't open it. */
    public record Match(String recordType, Long recordId, String name, String matchedOn) {}

    @Transactional(readOnly = true)
    public List<Match> check(User user, CheckReq req) {
        String type = req.type() == null ? "" : req.type().trim().toLowerCase(Locale.ROOT);
        if (!List.of("customer", "lead", "contact").contains(type)) {
            throw ApiException.badRequest("type must be customer, lead or contact");
        }
        if (user.getTenant() == null) {
            throw ApiException.forbidden("Only tenant users can check for duplicates");
        }
        Long tenantId = user.getTenant().getId();
        DataScope scope = dataScopeResolver.resolve(user);
        Map<String, Match> matches = new LinkedHashMap<>();

        String emailHash = req.email() != null && req.email().contains("@") ? encryption.emailIndex(req.email()) : null;
        if (emailHash != null) {
            add(matches, scope, "email", jdbc.queryForList("SELECT 'customer' AS type, id, company_name AS name, owner_id FROM customers "
                    + "WHERE tenant_id = ? AND email_hash = ? AND erased_at IS NULL AND id <> ?", tenantId, emailHash, exclude(req, type, "customer")));
            add(matches, scope, "email", jdbc.queryForList("SELECT 'customer' AS type, c.id, c.company_name || ' — ' || cc.name AS name, c.owner_id "
                    + "FROM customer_contacts cc JOIN customers c ON c.id = cc.customer_id "
                    + "WHERE c.tenant_id = ? AND cc.email_hash = ? AND c.erased_at IS NULL AND cc.id <> ?", tenantId, emailHash, exclude(req, type, "contact")));
            add(matches, scope, "email", jdbc.queryForList("SELECT 'lead' AS type, id, lead_name || ' (' || company_name || ')' AS name, owner_id FROM leads "
                    + "WHERE tenant_id = ? AND email_hash = ? AND erased_at IS NULL AND converted_customer_id IS NULL AND id <> ?",
                    tenantId, emailHash, exclude(req, type, "lead")));
        }

        String name = normalize(req.companyName());
        if (!type.equals("contact") && name.length() >= 2) {
            add(matches, scope, "company name", jdbc.queryForList("SELECT 'customer' AS type, id, company_name AS name, owner_id FROM customers "
                    + "WHERE tenant_id = ? AND erased_at IS NULL AND id <> ? AND " + NAME_SQL.formatted("company_name") + " = ?",
                    tenantId, exclude(req, type, "customer"), name));
            add(matches, scope, "company name", jdbc.queryForList("SELECT 'lead' AS type, id, lead_name || ' (' || company_name || ')' AS name, owner_id "
                    + "FROM leads WHERE tenant_id = ? AND erased_at IS NULL AND converted_customer_id IS NULL AND id <> ? AND "
                    + NAME_SQL.formatted("company_name") + " = ?", tenantId, exclude(req, type, "lead"), name));
            // FR-AI-9.1: near-identical customer names too ("Acme Industrie" for "Acme Industries")
            add(matches, scope, "similar company name", detection.similarNames(tenantId, req.companyName(), exclude(req, type, "customer")));
        }
        return new ArrayList<>(matches.values()).subList(0, Math.min(MAX_MATCHES, matches.size()));
    }

    /** "Acme Industries Pvt. Ltd." and "ACME INDUSTRIES PRIVATE LIMITED" both become "acmeindustries". */
    static String normalize(String companyName) {
        if (companyName == null) {
            return "";
        }
        return SUFFIXES.matcher(companyName.toLowerCase(Locale.ROOT)).replaceAll("").replaceAll("[^a-z0-9]", "");
    }

    /** The id of the record being edited, when it is of this kind (-1 matches nothing). */
    private static long exclude(CheckReq req, String type, String kind) {
        return req.excludeId() != null && type.equals(kind) ? req.excludeId() : -1L;
    }

    private static void add(Map<String, Match> matches, DataScope scope, String matchedOn, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String type = (String) row.get("type");
            long id = ((Number) row.get("id")).longValue();
            Long ownerId = row.get("owner_id") == null ? null : ((Number) row.get("owner_id")).longValue();
            boolean visible = scope.allows(ownerId);
            matches.putIfAbsent(type + ":" + id, new Match(type, visible ? id : null, visible ? (String) row.get("name") : null, matchedOn));
        }
    }
}
