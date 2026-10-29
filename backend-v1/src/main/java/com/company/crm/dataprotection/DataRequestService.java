package com.company.crm.dataprotection;

import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.ai.service.MeetingSummaryService;
import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.enums.CustomerStatus;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.storage.DocumentStorage;
import com.company.crm.communication.service.CommunicationService;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.customer.service.CustomerService;
import com.company.crm.lead.entity.Lead;
import com.company.crm.lead.repository.LeadRepository;
import com.company.crm.lead.service.LeadService;
import com.company.crm.user.entity.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SEC-8 (SRS 20.4): data-protection requests for a customer or lead. Each request is logged with a
 * 30-day due date. An export is a JSON file of everything the CRM holds about them (details,
 * contacts, consent, messages, meetings, quotations, deals, service requests, documents, custom
 * fields). An erasure removes their personal data — names, contact details, notes, message text,
 * documents — but keeps the business figures (quotations, deals) under an anonymous name. Tenant
 * admin only; every step is audited.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataRequestService {

    private static final int DUE_DAYS = 30;
    public static final String CONFIRM = "ERASE";
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.INDENT_OUTPUT);

    private final JdbcTemplate jdbc;
    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final CustomerService customerService;
    private final LeadService leadService;
    private final CommunicationService communicationService;
    private final DocumentStorage documentStorage;
    private final AuditService auditService;

    public record CreateReq(@NotNull @Pattern(regexp = "customer|lead") String subjectType, @NotNull Long subjectId,
                            @NotNull @Pattern(regexp = "export|erasure") String requestType, @Size(max = 1000) String notes) {}

    public record DataRequestRes(Long id, String subjectType, Long subjectId, String subjectName, String requestType, String status,
                                 String notes, LocalDateTime requestedAt, LocalDateTime dueAt, LocalDateTime completedAt, String outcome,
                                 boolean overdue) {}

    public record ExportFile(String fileName, byte[] content) {}

    @Transactional
    public DataRequestRes create(User admin, CreateReq req) {
        String name = "lead".equals(req.subjectType()) ? leadOf(admin, req.subjectId()).getLeadName() : customerOf(admin, req.subjectId()).getCompanyName();
        Long id = jdbc.queryForObject("INSERT INTO data_requests (tenant_id, subject_type, subject_id, subject_name, request_type, notes, requested_by, due_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                tenantId(admin), req.subjectType(), req.subjectId(), name, req.requestType(),
                req.notes() == null || req.notes().isBlank() ? null : req.notes().trim(), admin.getId(),
                Timestamp.valueOf(LocalDateTime.now().plusDays(DUE_DAYS)));
        auditService.record(admin, AuditAction.DATA_REQUEST_CREATED, req.subjectType(), req.subjectId(), req.requestType() + " request #" + id);
        return get(admin, id);
    }

    @Transactional(readOnly = true)
    public List<DataRequestRes> list(User admin) {
        return jdbc.query("SELECT * FROM data_requests WHERE tenant_id = ? ORDER BY requested_at DESC, id DESC LIMIT 200",
                (rs, i) -> toRes(rs.getLong("id"), rs.getString("subject_type"), rs.getLong("subject_id"), rs.getString("subject_name"),
                        rs.getString("request_type"), rs.getString("status"), rs.getString("notes"), rs.getTimestamp("requested_at"),
                        rs.getTimestamp("due_at"), rs.getTimestamp("completed_at"), rs.getString("outcome")), tenantId(admin));
    }

    /** The JSON export; completes an open export request. Any request's subject can be exported (e.g. before erasing). */
    @Transactional
    public ExportFile export(User admin, Long requestId) {
        DataRequestRes r = get(admin, requestId);
        Map<String, Object> data = "lead".equals(r.subjectType()) ? leadData(admin, r.subjectId()) : customerData(admin, r.subjectId());
        byte[] content;
        try {
            content = JSON.writeValueAsBytes(data);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the export", e);
        }
        if ("export".equals(r.requestType()) && "open".equals(r.status())) {
            complete(admin, requestId, "Exported " + LocalDateTime.now().toLocalDate());
        }
        auditService.record(admin, AuditAction.DATA_EXPORTED, r.subjectType(), r.subjectId(), "data request #" + requestId);
        return new ExportFile(r.subjectType() + "-" + r.subjectId() + "-personal-data.json", content);
    }

    /** Erases the subject's personal data (needs confirm = "ERASE"); completes the request. Cannot be undone. */
    @Transactional
    public DataRequestRes erase(User admin, Long requestId, String confirm) {
        DataRequestRes r = get(admin, requestId);
        if (!"erasure".equals(r.requestType()) || !"open".equals(r.status())) {
            throw ApiException.badRequest("Only an open erasure request can be carried out");
        }
        if (!CONFIRM.equals(confirm)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Type ERASE to confirm — this cannot be undone", "CONFIRMATION_REQUIRED");
        }
        String outcome = "lead".equals(r.subjectType()) ? eraseLead(admin, r.subjectId()) : eraseCustomer(admin, r.subjectId());
        jdbc.update("UPDATE data_requests SET subject_name = ? WHERE id = ?", ("lead".equals(r.subjectType()) ? "Erased lead #" : "Erased customer #") + r.subjectId(), requestId);
        complete(admin, requestId, outcome);
        auditService.record(admin, AuditAction.DATA_ERASED, r.subjectType(), r.subjectId(), "data request #" + requestId + ": " + outcome);
        return get(admin, requestId);
    }

    @Transactional
    public DataRequestRes reject(User admin, Long requestId, String reason) {
        DataRequestRes r = get(admin, requestId);
        if (!"open".equals(r.status())) {
            throw ApiException.badRequest("This request is already closed");
        }
        jdbc.update("UPDATE data_requests SET status = 'rejected', completed_by = ?, completed_at = now(), outcome = ? WHERE id = ?",
                admin.getId(), reason == null || reason.isBlank() ? "Rejected" : reason.trim(), requestId);
        auditService.record(admin, AuditAction.DATA_REQUEST_REJECTED, r.subjectType(), r.subjectId(), "data request #" + requestId);
        return get(admin, requestId);
    }

    // ==================== export ====================

    private Map<String, Object> customerData(User admin, Long id) {
        Customer c = customerOf(admin, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", LocalDateTime.now());
        out.put("type", "customer");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", c.getId());
        details.put("companyName", c.getCompanyName());
        details.put("industry", c.getIndustry());
        details.put("email", c.getEmail());
        details.put("phone", c.getPhone());
        details.put("website", c.getWebsite());
        details.put("address", c.getAddress());
        details.put("notes", c.getNotes());
        details.put("status", c.getStatus().getDbValue());
        details.put("createdAt", c.getCreatedAt());
        out.put("details", details);
        out.put("contacts", c.getContacts().stream().map(ct -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", ct.getName());
            m.put("designation", ct.getDesignation());
            m.put("email", ct.getEmail());
            m.put("phone", ct.getPhone());
            return m;
        }).toList());
        out.put("communicationPreferences", Map.of("marketingEmail", !c.isMarketingOptOut(), "sms", !c.isSmsOptOut(),
                "whatsapp", c.isWhatsappOptIn(), "excludedFromAi", c.isAiExcluded()));
        out.put("messagesAndCalls", communicationService.timeline(admin, id, null));
        out.put("meetings", rows("SELECT title, starts_at, ends_at, location, notes, status FROM meetings WHERE tenant_id = ? AND customer_id = ? ORDER BY starts_at", admin, id));
        out.put("quotations", rows("SELECT q.quotation_number, q.status, q.valid_until, q.sent_at, q.customer_approved_at, q.customer_response_comment,"
                + " (SELECT json_agg(json_build_object('product', i.product_name, 'quantity', i.quantity, 'unitPrice', i.unit_price, 'discountPercent', i.discount_percent))::text"
                + " FROM quotation_line_items i WHERE i.quotation_id = q.id) AS line_items FROM quotations q WHERE q.tenant_id = ? AND q.customer_id = ? ORDER BY q.created_at", admin, id));
        out.put("deals", rows("SELECT product_service, deal_value, stage, expected_closing_date, created_at FROM opportunities WHERE tenant_id = ? AND customer_id = ? ORDER BY created_at", admin, id));
        out.put("serviceRequests", rows("SELECT subject, description, priority, status, created_at, resolved_at, feedback_score, feedback_comment, reporter_email"
                + " FROM service_tickets WHERE tenant_id = ? AND customer_id = ? ORDER BY created_at", admin, id));
        out.put("documents", rows("SELECT file_name, content_type, size_bytes, created_at FROM customer_documents WHERE tenant_id = ? AND customer_id = ? ORDER BY created_at", admin, id));
        // FR-AI-7.1: which meetings were recorded and the consent given (the audio itself is available on request)
        out.put("meetingTranscripts", jdbc.query("SELECT title, starts_at, transcript FROM meetings WHERE tenant_id = ? AND customer_id = ? "
                        + "AND transcript IS NOT NULL ORDER BY starts_at", (rs, i) -> Map.of("meeting", rs.getString(1), "startsAt", rs.getTimestamp(2).toString(),
                        "transcript", DataEncryption.get().decrypt(rs.getString(3))), tenantId(admin), id)); // FR-AI-7.2
        out.put("meetingRecordings", rows("SELECT m.title AS meeting, r.duration_seconds, r.consent_given_by, r.consent_method, r.consent_at, r.created_at"
                + " FROM meeting_recordings r JOIN meetings m ON m.id = r.meeting_id WHERE r.tenant_id = ? AND m.customer_id = ? ORDER BY r.created_at", admin, id));
        out.put("industryDetails", customFields(admin, "customer", id));
        return out;
    }

    private Map<String, Object> leadData(User admin, Long id) {
        Lead l = leadOf(admin, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", LocalDateTime.now());
        out.put("type", "lead");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", l.getId());
        details.put("name", l.getLeadName());
        details.put("companyName", l.getCompanyName());
        details.put("industry", l.getIndustry() != null ? l.getIndustry().getDbValue() : null);
        details.put("source", l.getSource() != null ? l.getSource().getDbValue() : null);
        details.put("stage", l.getStage() != null ? l.getStage().getDbValue() : null);
        details.put("email", l.getContactEmail());
        details.put("phone", l.getContactPhone());
        details.put("notes", l.getNotes());
        details.put("followUpDate", l.getFollowUpDate());
        details.put("createdAt", l.getCreatedAt());
        out.put("details", details);
        out.put("excludedFromAi", l.isAiExcluded());
        out.put("messagesAndCalls", communicationService.timeline(admin, null, id));
        out.put("meetings", rows("SELECT title, starts_at, ends_at, location, notes, status FROM meetings WHERE tenant_id = ? AND lead_id = ? ORDER BY starts_at", admin, id));
        out.put("industryDetails", customFields(admin, "lead", id));
        return out;
    }

    private List<Map<String, Object>> rows(String sql, User admin, Long id) {
        return jdbc.queryForList(sql, tenantId(admin), id);
    }

    private Object customFields(User admin, String type, Long id) {
        List<String> json = jdbc.queryForList("SELECT field_values::text FROM custom_field_values WHERE tenant_id = ? AND entity_type = ? AND record_id = ?",
                String.class, tenantId(admin), type, id);
        if (json.isEmpty()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json.get(0), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            return json.get(0);
        }
    }

    // ==================== erasure ====================

    private String eraseCustomer(User admin, Long id) {
        Customer c = customerOf(admin, id);
        Long tenantId = tenantId(admin);
        LocalDateTime now = LocalDateTime.now();
        int contacts = c.getContacts().size();
        c.getContacts().clear();
        c.setCompanyName("Erased customer #" + id);
        c.setEmail(null);
        c.setPhone(null);
        c.setWebsite(null);
        c.setAddress(null);
        c.setNotes(null);
        c.setStatus(CustomerStatus.ARCHIVED);
        c.setMarketingOptOut(true);
        c.setMarketingOptOutAt(now);
        c.setSmsOptOut(true);
        c.setSmsOptOutAt(now);
        c.setWhatsappOptIn(false);
        c.setWhatsappOptInAt(null);
        c.setConsentUpdatedAt(now);
        c.setConsentSource("erased");
        c.setAiExcluded(true);
        c.setErasedAt(now);
        customerRepository.save(c);

        int messages = jdbc.update("UPDATE communication_logs SET subject = NULL, body = '[erased]', counterpart = NULL, counterpart_hash = NULL,"
                + " error_message = NULL WHERE tenant_id = ? AND customer_id = ?", tenantId, id);
        jdbc.update("UPDATE meetings SET title = 'Meeting', location = NULL, notes = NULL WHERE tenant_id = ? AND customer_id = ?", tenantId, id);
        MeetingSummaryService.clear(jdbc, "tenant_id = ? AND customer_id = ?", tenantId, id); // FR-AI-7.2
        jdbc.update("UPDATE follow_up_tasks SET title = 'Follow-up', notes = NULL WHERE tenant_id = ? AND customer_id = ?", tenantId, id); // FR-AI-7.3
        jdbc.update("UPDATE service_tickets SET description = NULL, feedback_comment = NULL, reporter_email = NULL WHERE tenant_id = ? AND customer_id = ?",
                tenantId, id);
        jdbc.update("UPDATE quotations SET customer_response_comment = NULL WHERE tenant_id = ? AND customer_id = ?", tenantId, id);
        jdbc.update("UPDATE campaign_recipients SET address = NULL WHERE customer_id = ?", id);
        jdbc.update("UPDATE notification_deliveries SET recipient_name = ? WHERE tenant_id = ? AND recipient_type = 'customer' AND recipient_id = ?",
                "Erased customer #" + id, tenantId, id);
        jdbc.update("DELETE FROM custom_field_values WHERE tenant_id = ? AND entity_type = 'customer' AND record_id = ?", tenantId, id);
        int documents = deleteFiles("SELECT storage_key FROM customer_documents WHERE tenant_id = ? AND customer_id = ?",
                "DELETE FROM customer_documents WHERE tenant_id = ? AND customer_id = ?", tenantId, id);
        documents += deleteFiles("SELECT storage_key FROM attachments WHERE tenant_id = ? AND entity_type = 'service_ticket'"
                        + " AND entity_id IN (SELECT id FROM service_tickets WHERE customer_id = ?)",
                "DELETE FROM attachments WHERE tenant_id = ? AND entity_type = 'service_ticket' AND entity_id IN (SELECT id FROM service_tickets WHERE customer_id = ?)",
                tenantId, id);
        documents += eraseRecordings(tenantId, "customer_id", id); // FR-AI-7.1
        // The leads this customer came from hold the same person's details.
        List<Long> leads = jdbc.queryForList("SELECT id FROM leads WHERE tenant_id = ? AND converted_customer_id = ?", Long.class, tenantId, id);
        leads.forEach(leadId -> eraseLeadData(tenantId, leadId));
        return "erased details, " + contacts + " contact(s), " + messages + " message(s), " + documents + " file(s)"
                + (leads.isEmpty() ? "" : ", " + leads.size() + " original lead(s)") + "; quotations and deals kept anonymously";
    }

    private String eraseLead(User admin, Long id) {
        leadOf(admin, id);
        int messages = eraseLeadData(tenantId(admin), id);
        return "erased details and " + messages + " message(s)";
    }

    private int eraseLeadData(Long tenantId, Long leadId) {
        Lead l = leadRepository.findById(leadId).orElseThrow();
        l.setLeadName("Erased lead #" + leadId);
        l.setContactEmail(null);
        l.setContactPhone(null);
        l.setNotes(null);
        l.setAiExcluded(true);
        l.setErasedAt(LocalDateTime.now());
        leadRepository.save(l);
        int messages = jdbc.update("UPDATE communication_logs SET subject = NULL, body = '[erased]', counterpart = NULL, counterpart_hash = NULL,"
                + " error_message = NULL WHERE tenant_id = ? AND lead_id = ?", tenantId, leadId);
        jdbc.update("UPDATE meetings SET title = 'Meeting', location = NULL, notes = NULL WHERE tenant_id = ? AND lead_id = ?", tenantId, leadId);
        MeetingSummaryService.clear(jdbc, "tenant_id = ? AND lead_id = ?", tenantId, leadId); // FR-AI-7.2
        jdbc.update("UPDATE follow_up_tasks SET title = 'Follow-up', notes = NULL WHERE tenant_id = ? AND lead_id = ?", tenantId, leadId); // FR-AI-7.3
        jdbc.update("DELETE FROM custom_field_values WHERE tenant_id = ? AND entity_type = 'lead' AND record_id = ?", tenantId, leadId);
        eraseRecordings(tenantId, "lead_id", leadId); // FR-AI-7.1
        jdbc.update("UPDATE notification_deliveries SET recipient_name = ? WHERE tenant_id = ? AND recipient_type = 'lead' AND recipient_id = ?",
                "Erased lead #" + leadId, tenantId, leadId);
        return messages;
    }

    /** FR-AI-7.1: the audio of every recorded meeting with the customer or lead ({@code column} is a fixed name). */
    private int eraseRecordings(Long tenantId, String column, Long id) {
        String meetings = "(SELECT id FROM meetings WHERE tenant_id = ? AND " + column + " = ?)";
        return deleteFiles("SELECT storage_key FROM meeting_recordings WHERE meeting_id IN " + meetings,
                "DELETE FROM meeting_recordings WHERE meeting_id IN " + meetings, tenantId, id);
    }

    /** Deletes the stored files (a missing file is fine) and then their rows. */
    private int deleteFiles(String selectKeys, String deleteRows, Object... args) {
        List<String> keys = jdbc.queryForList(selectKeys, String.class, args);
        for (String key : keys) {
            try {
                documentStorage.delete(key);
            } catch (IOException e) {
                log.warn("Could not delete stored file {}: {}", key, e.getMessage());
            }
        }
        jdbc.update(deleteRows, args);
        return keys.size();
    }

    // ==================== helpers ====================

    private DataRequestRes get(User admin, Long id) {
        return jdbc.query("SELECT * FROM data_requests WHERE id = ? AND tenant_id = ?",
                        (rs, i) -> toRes(rs.getLong("id"), rs.getString("subject_type"), rs.getLong("subject_id"), rs.getString("subject_name"),
                                rs.getString("request_type"), rs.getString("status"), rs.getString("notes"), rs.getTimestamp("requested_at"),
                                rs.getTimestamp("due_at"), rs.getTimestamp("completed_at"), rs.getString("outcome")), id, tenantId(admin))
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Data request not found"));
    }

    private void complete(User admin, Long id, String outcome) {
        jdbc.update("UPDATE data_requests SET status = 'completed', completed_by = ?, completed_at = now(), outcome = ? WHERE id = ?",
                admin.getId(), outcome.length() > 1000 ? outcome.substring(0, 1000) : outcome, id);
    }

    private static DataRequestRes toRes(Long id, String type, Long subjectId, String name, String requestType, String status, String notes,
                                        Timestamp requested, Timestamp due, Timestamp completed, String outcome) {
        LocalDateTime dueAt = due.toLocalDateTime();
        return new DataRequestRes(id, type, subjectId, name, requestType, status, notes, requested.toLocalDateTime(), dueAt,
                completed != null ? completed.toLocalDateTime() : null, outcome, "open".equals(status) && dueAt.isBefore(LocalDateTime.now()));
    }

    /** The customer, through its own access rules (404 for another tenant's). */
    private Customer customerOf(User admin, Long id) {
        customerService.getCustomer(admin, id);
        return customerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Customer not found"));
    }

    private Lead leadOf(User admin, Long id) {
        leadService.getLead(admin, id);
        return leadRepository.findById(id).orElseThrow(() -> ApiException.notFound("Lead not found"));
    }

    private static Long tenantId(User user) {
        if (user.getTenant() == null) {
            throw ApiException.forbidden("Data requests are per tenant");
        }
        return user.getTenant().getId();
    }
}
