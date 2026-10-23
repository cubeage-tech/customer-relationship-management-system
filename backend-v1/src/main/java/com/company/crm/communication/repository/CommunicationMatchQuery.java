package com.company.crm.communication.repository;

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

    public List<Match> byEmail(String email) {
        String value = email.trim().toLowerCase();
        List<Match> matches = new ArrayList<>();
        matches.addAll(run("SELECT tenant_id, id, NULL FROM customers WHERE lower(email) = :v", value));
        matches.addAll(run("SELECT cu.tenant_id, cu.id, NULL FROM customer_contacts cc JOIN customers cu ON cu.id = cc.customer_id"
                + " WHERE lower(cc.email) = :v", value));
        matches.addAll(run("SELECT tenant_id, NULL, id FROM leads WHERE lower(contact_email) = :v AND converted_customer_id IS NULL", value));
        return matches.stream().distinct().toList();
    }

    public List<Match> byPhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 10) {
            return List.of();
        }
        String last10 = digits.substring(digits.length() - 10);
        List<Match> matches = new ArrayList<>();
        matches.addAll(run("SELECT tenant_id, id, NULL FROM customers"
                + " WHERE right(regexp_replace(phone, '\\D', '', 'g'), 10) = :v", last10));
        matches.addAll(run("SELECT cu.tenant_id, cu.id, NULL FROM customer_contacts cc JOIN customers cu ON cu.id = cc.customer_id"
                + " WHERE right(regexp_replace(cc.phone, '\\D', '', 'g'), 10) = :v", last10));
        matches.addAll(run("SELECT tenant_id, NULL, id FROM leads"
                + " WHERE right(regexp_replace(contact_phone, '\\D', '', 'g'), 10) = :v AND converted_customer_id IS NULL", last10));
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
