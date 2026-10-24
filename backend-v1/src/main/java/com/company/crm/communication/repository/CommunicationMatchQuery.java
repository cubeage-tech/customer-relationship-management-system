package com.company.crm.communication.repository;

import com.company.crm.common.crypto.DataEncryption;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds which customer/lead an inbound email or message belongs to when it is not a reply to
 * something we sent. Searches every tenant (one shared mailbox / number), so callers must only
 * accept an unambiguous match. Phones are compared on their last 10 digits, ignoring formatting.
 */
@Repository
public class CommunicationMatchQuery {

    /** A candidate owner of an inbound message. leadId is set only for unconverted leads. */
    public record Match(Long tenantId, Long customerId, Long leadId) {}

    @PersistenceContext
    private EntityManager entityManager;

    /** Case-insensitive address match, via the NFR-7.2b blind indexes (the addresses are encrypted). */
    public List<Match> byEmail(String email) {
        String hash = DataEncryption.get().emailIndex(email);
        if (hash == null) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        matches.addAll(run("SELECT tenant_id, id, NULL FROM customers WHERE email_hash = :v", hash));
        matches.addAll(run("SELECT cu.tenant_id, cu.id, NULL FROM customer_contacts cc JOIN customers cu ON cu.id = cc.customer_id"
                + " WHERE cc.email_hash = :v", hash));
        matches.addAll(run("SELECT tenant_id, NULL, id FROM leads WHERE email_hash = :v AND converted_customer_id IS NULL", hash));
        return matches.stream().distinct().toList();
    }

    /** Match on the last 10 digits, via the NFR-7.2b blind indexes. */
    public List<Match> byPhone(String phone) {
        String hash = DataEncryption.get().phoneIndex(phone);
        if (hash == null) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        matches.addAll(run("SELECT tenant_id, id, NULL FROM customers WHERE phone_hash = :v", hash));
        matches.addAll(run("SELECT cu.tenant_id, cu.id, NULL FROM customer_contacts cc JOIN customers cu ON cu.id = cc.customer_id"
                + " WHERE cc.phone_hash = :v", hash));
        matches.addAll(run("SELECT tenant_id, NULL, id FROM leads WHERE phone_hash = :v AND converted_customer_id IS NULL", hash));
        return matches.stream().distinct().toList();
    }

    @SuppressWarnings("unchecked")
    private List<Match> run(String sql, String value) {
        List<Object[]> rows = entityManager.createNativeQuery(sql).setParameter("v", value).getResultList();
        return rows.stream()
                .map(r -> new Match(toLong(r[0]), toLong(r[1]), toLong(r[2])))
                .toList();
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
