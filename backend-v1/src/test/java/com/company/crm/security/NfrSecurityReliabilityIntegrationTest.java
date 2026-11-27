package com.company.crm.security;

import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.common.crypto.PiiEncryptionBackfill;
import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.common.mail.MailOutboxService;
import com.company.crm.common.mail.MailQueuedException;
import com.company.crm.communication.integration.TwilioClient;
import com.company.crm.communication.scheduler.OutboundRetryJob;
import com.company.crm.communication.service.CommunicationService;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NFR-7.2b encryption at rest, NFR-7.2d audit trail, NFR-7.4a outbound retries and NFR-7.6b
 * per-tenant thresholds.
 */
class NfrSecurityReliabilityIntegrationTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CommunicationService communicationService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PiiEncryptionBackfill backfill;
    @Autowired private OutboundRetryJob retryJob;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transactions;

    private TestTenant tenant;
    private String adminToken;

    @BeforeEach
    void setUp() {
        tenant = testTenants.tenantWithAllRoles("NFR Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
    }

    // ==================== NFR-7.2b ====================

    @Test
    void personalDataIsEncryptedAtRestYetStillUsable() throws Exception {
        String email = "Buyer." + System.nanoTime() + "@Acme.test";
        // A number no other test uses, so the inbound match is unambiguous across tenants.
        String digits = String.valueOf(7_000_000_000L + System.nanoTime() % 1_000_000_000L);
        String phone = "+91 " + digits.substring(0, 5) + " " + digits.substring(5);
        long customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Acme\",\"industry\":\"trading\",\"email\":\"" + email
                + "\",\"phone\":\"" + phone + "\",\"address\":\"12 MG Road, Pune\"}"));
        send(get("/api/customers/" + customer), adminToken, null)
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.phone").value(phone))
                .andExpect(jsonPath("$.data.address").value("12 MG Road, Pune"));

        Map<String, Object> raw = jdbc.queryForMap("SELECT email, phone, address, email_hash, phone_hash FROM customers WHERE id = ?", customer);
        assertThat((String) raw.get("email")).startsWith(DataEncryption.PREFIX).doesNotContain("Acme");
        assertThat((String) raw.get("phone")).startsWith(DataEncryption.PREFIX).doesNotContain(digits.substring(0, 5));
        assertThat((String) raw.get("address")).startsWith(DataEncryption.PREFIX).doesNotContain("MG Road");
        assertThat(raw.get("email_hash")).isNotNull();

        // Inbound email and SMS still find the customer (case-insensitive email, last 10 digits of the phone).
        communicationService.recordInbound(new CommunicationService.InboundMessage(CommunicationChannel.EMAIL, email.toUpperCase(),
                "Re: offer", "Interested", "<in." + System.nanoTime() + "@mail>", List.of(), LocalDateTime.now()));
        communicationService.recordInbound(new CommunicationService.InboundMessage(CommunicationChannel.SMS, "+91" + digits,
                null, "Call me", "SM" + System.nanoTime(), List.of(), LocalDateTime.now()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM communication_logs WHERE customer_id = ? AND direction = 'inbound'", Long.class, customer))
                .isEqualTo(2L);
        assertThat(jdbc.queryForList("SELECT counterpart FROM communication_logs WHERE customer_id = ?", String.class, customer))
                .allMatch(c -> c.startsWith(DataEncryption.PREFIX));

        // Rows written before encryption are encrypted (and indexed) by the startup backfill.
        jdbc.update("UPDATE customers SET email = 'legacy@old.test', email_hash = NULL WHERE id = ?", customer);
        backfill.run(null);
        Map<String, Object> after = jdbc.queryForMap("SELECT email, email_hash FROM customers WHERE id = ?", customer);
        assertThat((String) after.get("email")).startsWith(DataEncryption.PREFIX);
        assertThat(after.get("email_hash")).isEqualTo(DataEncryption.get().emailIndex("legacy@old.test"));
        send(get("/api/customers/" + customer), adminToken, null).andExpect(jsonPath("$.data.email").value("legacy@old.test"));

        // The signup bank account (a payment detail) is encrypted too.
        jdbc.update("UPDATE tenants SET bank_account_number = '123456789012' WHERE id = ?", tenant.tenant().getId());
        backfill.run(null);
        assertThat(jdbc.queryForObject("SELECT bank_account_number FROM tenants WHERE id = ?", String.class, tenant.tenant().getId()))
                .startsWith(DataEncryption.PREFIX);
    }

    @Test
    void resetLinksAreStoredOnlyAsHashes() throws Exception {
        String email = tenant.user(SALES_EXECUTIVE).getEmail();
        send(post("/auth/forgot-password"), null, "{\"email\":\"" + email + "\"}").andExpect(status().isOk());
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendPasswordResetEmail(eq(email), anyString(), raw.capture());
        String stored = jdbc.queryForObject("SELECT token FROM password_reset_tokens WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                String.class, tenant.user(SALES_EXECUTIVE).getId());
        assertThat(stored).isEqualTo(DataEncryption.sha256(raw.getValue())).isNotEqualTo(raw.getValue());

        send(post("/auth/reset-password"), null, "{\"token\":\"" + stored + "\",\"newPassword\":\"N3w-Passw0rd!x\"}")
                .andExpect(status().is4xxClientError()); // the stored hash is not a usable link
        send(post("/auth/reset-password"), null, "{\"token\":\"" + raw.getValue() + "\",\"newPassword\":\"N3w-Passw0rd!x\"}")
                .andExpect(status().isOk());
    }

    // ==================== NFR-7.2d ====================

    @Test
    void financialAndUserChangesAreAuditedAndVisibleToAdminsOnly() throws Exception {
        long customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Ledger Ltd\",\"industry\":\"trading\"}"));
        long quotation = idOf(send(post("/api/quotations"), tenant.token(SALES_EXECUTIVE), "{\"customerId\":" + customer
                + ",\"lineItems\":[{\"productName\":\"Kit\",\"quantity\":2,\"unitPrice\":1000,\"discountPercent\":40}]}"));
        send(patch("/api/quotations/" + quotation + "/discount/approve"), tenant.token(FINANCE_APPROVER), null).andExpect(status().isOk());
        long deal = idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customer + ",\"dealValue\":5000}"));
        send(patch("/api/opportunities/" + deal + "/stage"), adminToken, "{\"stage\":\"won\",\"force\":true}").andExpect(status().isOk());
        send(post("/api/commission-rules"), adminToken, "{\"name\":\"Flat\",\"priority\":1,\"ratePercent\":5}").andExpect(status().isOk());
        send(post("/api/users"), adminToken, "{\"fullName\":\"New Hire\",\"email\":\"hire" + System.nanoTime() + "@nfr.test\","
                + "\"password\":\"Str0ng!Passw0rd\",\"role\":\"sales_executive\"}").andExpect(status().isOk());

        send(get("/api/audit-events"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].action").value(hasItems("CUSTOMER_CREATED", "QUOTATION_CREATED", "DISCOUNT_APPROVED",
                        "OPPORTUNITY_CREATED", "OPPORTUNITY_STAGE_CHANGED", "COMMISSION_RULE_CREATED", "USER_CREATED")))
                .andExpect(jsonPath("$.data.items[?(@.action == 'DISCOUNT_APPROVED')].actorName")
                        .value(hasItem(tenant.user(FINANCE_APPROVER).getFullName())))
                .andExpect(jsonPath("$.data.items[?(@.action == 'QUOTATION_CREATED')].details").value(hasItem(org.hamcrest.Matchers.containsString("maxDiscount=40"))));
        send(get("/api/audit-events").param("entityType", "quotation"), tenant.token(EXECUTIVE_OWNER), null)
                .andExpect(jsonPath("$.data.items[*].entityType").value(not(hasItem("customer"))))
                .andExpect(jsonPath("$.data.total").value(2));
        send(get("/api/audit-events").param("action", "USER_CREATED"), adminToken, null).andExpect(jsonPath("$.data.total").value(1));
        send(get("/api/audit-events").param("action", "BOGUS"), adminToken, null).andExpect(status().isBadRequest());

        // Only admins/owners; never another tenant's trail; the platform admin can pick a tenant.
        send(get("/api/audit-events"), tenant.token(SALES_MANAGER), null).andExpect(status().isForbidden());
        TestTenant other = testTenants.tenantWithAllRoles("Other NFR " + System.nanoTime());
        send(get("/api/audit-events"), other.token(ADMIN), null).andExpect(jsonPath("$.data.total").value(0));
        send(get("/api/audit-events").param("tenantId", String.valueOf(tenant.tenant().getId())), other.token(ADMIN), null)
                .andExpect(status().isForbidden());
        send(get("/api/audit-events").param("tenantId", String.valueOf(tenant.tenant().getId())).param("action", "USER_CREATED"),
                testTenants.superAdminToken(), null)
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].tenantName").value(tenant.tenant().getCompanyName()));
    }

    // ==================== NFR-7.4a ====================

    @Test
    void anEmailTheMailServerRejectedIsQueuedThenMarkedSentWhenARetryGetsThrough() throws Exception {
        long customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Retry Co\",\"industry\":\"trading\"}"));
        doThrow(new MailQueuedException("Mail server unavailable", new RuntimeException("connect timed out")))
                .when(mailService).sendCustomerEmail(anyString(), anyString(), anyString(), anyString());
        String sent = send(post("/api/communications/email"), adminToken, "{\"customerId\":" + customer
                + ",\"to\":\"buyer@retry.test\",\"subject\":\"Offer\",\"body\":\"Hello\"}")
                .andExpect(jsonPath("$.data.status").value("queued"))
                .andExpect(jsonPath("$.data.errorMessage").value(org.hamcrest.Matchers.containsString("retrying automatically")))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(sent, "$.data.id")).longValue();
        String messageId = jdbc.queryForObject("SELECT provider_message_id FROM communication_logs WHERE id = ?", String.class, id);

        // The mail outbox delivers it on a later attempt and reports the outcome.
        events.publishEvent(new MailOutboxService.Outcome(messageId, true, null));
        Map<String, Object> row = jdbc.queryForMap("SELECT status, error_message FROM communication_logs WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("sent");
        assertThat(row.get("error_message")).isNull();
    }

    @Test
    void temporarySmsFailuresAreRetriedAndRefusalsAreNot() throws Exception {
        long customerId = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Sms Co\",\"industry\":\"trading\",\"phone\":\"+919812345678\"}"));
        when(twilioClient.isSmsEnabled()).thenReturn(true);
        when(twilioClient.sendSms(anyString(), anyString()))
                .thenThrow(new TwilioClient.TwilioException("Message provider did not respond", true))
                .thenReturn(new TwilioClient.SendResult("SM-ok", "queued"));
        Long id = transactions.execute(s -> communicationService.sendAutomatedSms(tenant.tenant(),
                customerRepository.findById(customerId).orElseThrow(), "+919812345678", "Your quotation is ready").getId());

        Map<String, Object> queued = jdbc.queryForMap("SELECT status, next_retry_at, retry_count FROM communication_logs WHERE id = ?", id);
        assertThat(queued.get("status")).isEqualTo("queued");
        assertThat(queued.get("next_retry_at")).isNotNull();
        jdbc.update("UPDATE communication_logs SET next_retry_at = ? WHERE id = ?", LocalDateTime.now().minus(Duration.ofMinutes(1)), id);
        retryJob.retryDue();
        Map<String, Object> retried = jdbc.queryForMap("SELECT status, next_retry_at, retry_count, provider_message_id FROM communication_logs WHERE id = ?", id);
        assertThat(retried.get("provider_message_id")).isEqualTo("SM-ok");
        assertThat(retried.get("next_retry_at")).isNull();
        assertThat(retried.get("retry_count")).isEqualTo(1);

        // A refusal (e.g. invalid number) fails straight away, with no retry scheduled.
        reset(twilioClient);
        when(twilioClient.isSmsEnabled()).thenReturn(true);
        when(twilioClient.sendSms(anyString(), anyString())).thenThrow(new TwilioClient.TwilioException("Invalid 'To' number", false));
        Long refused = transactions.execute(s -> communicationService.sendAutomatedSms(tenant.tenant(),
                customerRepository.findById(customerId).orElseThrow(), "+919812345678", "Hi").getId());
        Map<String, Object> failed = jdbc.queryForMap("SELECT status, next_retry_at FROM communication_logs WHERE id = ?", refused);
        assertThat(failed.get("status")).isEqualTo("failed");
        assertThat(failed.get("next_retry_at")).isNull();
    }

    // ==================== NFR-7.6b ====================

    @Test
    void slaHoursAndDiscountThresholdAreConfigurablePerTenant() throws Exception {
        send(get("/api/tenant-settings"), adminToken, null)
                .andExpect(jsonPath("$.data.slaHoursCritical.value").value(4))
                .andExpect(jsonPath("$.data.slaHoursCritical.custom").value(false))
                .andExpect(jsonPath("$.data.discountApprovalThreshold.value").value(15));
        send(put("/api/tenant-settings"), tenant.token(SALES_MANAGER), "{\"slaHoursCritical\":2}").andExpect(status().isForbidden());
        send(put("/api/tenant-settings"), adminToken, "{\"slaHoursCritical\":0}").andExpect(status().isBadRequest());
        send(put("/api/tenant-settings"), adminToken, "{\"slaHoursCritical\":2,\"discountApprovalThreshold\":30}")
                .andExpect(jsonPath("$.data.slaHoursCritical.value").value(2))
                .andExpect(jsonPath("$.data.slaHoursCritical.custom").value(true))
                .andExpect(jsonPath("$.data.slaHoursHigh.value").value(8))
                .andExpect(jsonPath("$.data.discountApprovalThreshold.value").value(30));

        long customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Sla Co\",\"industry\":\"trading\"}"));
        String ticket = send(post("/api/tickets"), adminToken, "{\"customerId\":" + customer + ",\"subject\":\"Down\",\"priority\":\"critical\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        LocalDateTime due = LocalDateTime.parse(JsonPath.read(ticket, "$.data.slaDueAt"));
        assertThat(Duration.between(LocalDateTime.now(), due).toMinutes()).isBetween(110L, 121L);

        // 20% is under this tenant's 30% threshold, so no approval is needed.
        send(post("/api/quotations"), adminToken, "{\"customerId\":" + customer
                + ",\"lineItems\":[{\"productName\":\"Kit\",\"quantity\":1,\"unitPrice\":100,\"discountPercent\":20}]}")
                .andExpect(jsonPath("$.data.discountApprovalStatus").value("not_required"));
        // Another tenant still uses the default (15%).
        TestTenant other = testTenants.tenantWithAllRoles("Default NFR " + System.nanoTime());
        long otherCustomer = idOf(send(post("/api/customers"), other.token(ADMIN), "{\"companyName\":\"Def Co\",\"industry\":\"trading\"}"));
        send(post("/api/quotations"), other.token(ADMIN), "{\"customerId\":" + otherCustomer
                + ",\"lineItems\":[{\"productName\":\"Kit\",\"quantity\":1,\"unitPrice\":100,\"discountPercent\":20}]}")
                .andExpect(jsonPath("$.data.discountApprovalStatus").value("pending"));

        send(get("/api/audit-events").param("action", "TENANT_SETTINGS_UPDATED"), adminToken, null)
                .andExpect(jsonPath("$.data.items[0].details").value(org.hamcrest.Matchers.containsString("slaHoursCritical: default → 2")));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
