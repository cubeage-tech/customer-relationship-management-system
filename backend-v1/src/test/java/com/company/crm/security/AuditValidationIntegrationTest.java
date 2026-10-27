package com.company.crm.security;

import com.company.crm.integration.calllog.IntegrationCallLog;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-1/VAL-7 (stage order with logged overrides), VAL-1/VAL-3 (duplicate warnings, name length),
 * SEC-1b (password policy), SEC-7 (sign-ins, record changes and exports in the audit trail) and
 * INT-9 (outbound integration call log).
 */
class AuditValidationIntegrationTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private IntegrationCallLog callLog;

    private TestTenant tenant;
    private String adminToken;
    private long customerId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Audit Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        customerId = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Order Buyer " + System.nanoTime() + "\",\"industry\":\"trading\"}"));
    }

    // ==================== BR-1 ====================

    @Test
    void skippingLeadStagesNeedsAnOverrideThatIsAudited() throws Exception {
        long lead = idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Ravi\",\"companyName\":\"Skip Co\",\"industry\":\"trading\",\"source\":\"referral\"}"));
        send(patch("/api/leads/" + lead + "/stage"), adminToken, "{\"stage\":\"meeting\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("STAGE_SKIP"));
        send(patch("/api/leads/" + lead + "/stage"), adminToken, "{\"stage\":\"meeting\",\"force\":true,\"overrideReason\":\"Met at the expo\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stage").value("meeting"));
        send(patch("/api/leads/" + lead + "/stage"), adminToken, "{\"stage\":\"quotation\"}").andExpect(status().isOk()); // next stage: no override

        List<String> overrides = jdbc.queryForList("SELECT details FROM audit_events WHERE action = 'LEAD_STAGE_OVERRIDDEN' AND entity_id = ?",
                String.class, lead);
        assertThat(overrides).containsExactly("stage new_lead → meeting, skipping 1 stage. Reason: Met at the expo");
    }

    // ==================== VAL-7 ====================

    @Test
    void opportunitiesFollowTheConfiguredOrderAndOverridesAreLogged() throws Exception {
        long deal = idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customerId + ",\"dealValue\":1000}"));
        stage(deal, "{\"stage\":\"negotiation\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("STAGE_SKIP"));
        stage(deal, "{\"stage\":\"won\"}").andExpect(status().isBadRequest());
        stage(deal, "{\"stage\":\"proposal\"}").andExpect(status().isOk());       // the next stage
        stage(deal, "{\"stage\":\"qualification\"}").andExpect(status().isOk());  // back
        stage(deal, "{\"stage\":\"lost\",\"lossReason\":\"Price\"}").andExpect(status().isOk()); // Lost at any time
        stage(deal, "{\"stage\":\"proposal\"}").andExpect(status().isOk());       // reopened
        stage(deal, "{\"stage\":\"won\",\"force\":true,\"overrideReason\":\"Signed early\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT details FROM audit_events WHERE action = 'OPPORTUNITY_STAGE_OVERRIDDEN' AND entity_id = ?",
                String.class, deal)).containsExactly("stage proposal → won, skipping 1 stage. Reason: Signed early");

        send(delete("/api/opportunities/" + deal), adminToken, null).andExpect(status().isOk());
        assertThat(count("OPPORTUNITY_DELETED", "opportunity", deal)).isEqualTo(1);
    }

    // ==================== VAL-1 / VAL-3 ====================

    @Test
    void possibleDuplicatesAreFlaggedWithinTheTenant() throws Exception {
        long n = System.nanoTime();
        long acme = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Acme Tools " + n + " Pvt. Ltd.\",\"industry\":\"trading\","
                + "\"email\":\"Buyer" + n + "@Acme.test\"}"));

        // The same email in another case, and the same company written differently.
        check(adminToken, "{\"type\":\"customer\",\"email\":\"buyer" + n + "@acme.test\"}")
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].recordType").value("customer"))
                .andExpect(jsonPath("$.data[0].recordId").value(acme))
                .andExpect(jsonPath("$.data[0].matchedOn").value("email"));
        check(adminToken, "{\"type\":\"lead\",\"companyName\":\"ACME TOOLS " + n + " PRIVATE LIMITED\"}")
                .andExpect(jsonPath("$.data[0].recordId").value(acme))
                .andExpect(jsonPath("$.data[0].matchedOn").value("company name"));
        // Editing the record itself isn't a duplicate of it.
        check(adminToken, "{\"type\":\"customer\",\"companyName\":\"Acme Tools " + n + "\",\"excludeId\":" + acme + "}")
                .andExpect(jsonPath("$.data.length()").value(0));

        // Contacts and leads are matched by email too.
        send(post("/api/customers/" + customerId + "/contacts"), adminToken, "{\"name\":\"Meera\",\"email\":\"meera" + n + "@buyer.test\"}")
                .andExpect(status().isOk());
        check(adminToken, "{\"type\":\"lead\",\"email\":\"MEERA" + n + "@buyer.test\"}")
                .andExpect(jsonPath("$.data[0].recordId").value(customerId))
                .andExpect(jsonPath("$.data[0].name").value(containsString("Meera")));
        long lead = idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Kiran\",\"companyName\":\"Kiran Traders\",\"industry\":\"trading\","
                + "\"source\":\"referral\",\"contactEmail\":\"kiran" + n + "@traders.test\"}"));
        check(adminToken, "{\"type\":\"contact\",\"email\":\"kiran" + n + "@traders.test\"}")
                .andExpect(jsonPath("$.data[0].recordType").value("lead"))
                .andExpect(jsonPath("$.data[0].recordId").value(lead));

        // Someone who can't open the record learns only that it exists.
        check(tenant.token(SALES_EXECUTIVE), "{\"type\":\"customer\",\"companyName\":\"Acme Tools " + n + "\"}")
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].recordId").doesNotExist())
                .andExpect(jsonPath("$.data[0].name").doesNotExist());
        // Another tenant sees nothing.
        TestTenant other = testTenants.tenantWithAllRoles("Other Dup " + n);
        check(other.token(ADMIN), "{\"type\":\"customer\",\"companyName\":\"Acme Tools " + n + "\",\"email\":\"buyer" + n + "@acme.test\"}")
                .andExpect(jsonPath("$.data.length()").value(0));

        // VAL-3: a one-letter company name is refused.
        send(post("/api/customers"), adminToken, "{\"companyName\":\"A\",\"industry\":\"trading\"}").andExpect(status().isBadRequest());
        send(post("/api/leads"), adminToken, "{\"leadName\":\"X\",\"companyName\":\"B\",\"industry\":\"trading\",\"source\":\"referral\"}")
                .andExpect(status().isBadRequest());
    }

    // ==================== SEC-1b ====================

    @Test
    void passwordsMustBeStrongAndNotRecentlyUsed() throws Exception {
        mockMvc.perform(get("/auth/password-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minLength").value(10))
                .andExpect(jsonPath("$.data.minCharacterKinds").value(3))
                .andExpect(jsonPath("$.data.history").value(5));

        String email = "priya" + System.nanoTime() + "@owner.test";
        for (String weak : List.of("Short1!", "abcdefghijkl", "password@123", "Sharma@2026x")) {
            postJson("/auth/signup", "{\"name\":\"Priya Sharma\",\"email\":\"" + email + "\",\"password\":\"" + weak + "\","
                    + "\"organizationName\":\"Strong Co\",\"bankAccountNumber\":\"123456789\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("WEAK_PASSWORD"));
        }
        postJson("/auth/signup", "{\"name\":\"Priya Sharma\",\"email\":\"" + email + "\",\"password\":\"Blue#Kettle42\","
                + "\"organizationName\":\"Strong Co\",\"bankAccountNumber\":\"123456789\"}").andExpect(status().isOk());

        // Reset: the current and recent passwords can't come back; a refused one leaves the link usable.
        User user = tenant.user(SALES_MANAGER);
        user.setPasswordHash(passwordEncoder.encode("Old-Passw0rd!x"));
        userRepository.save(user);
        String link = resetLink(user);
        postJson("/auth/reset-password", "{\"token\":\"" + link + "\",\"newPassword\":\"Old-Passw0rd!x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a password you haven't used recently"));
        postJson("/auth/reset-password", "{\"token\":\"" + link + "\",\"newPassword\":\"Fresh-Start#2026\"}").andExpect(status().isOk());
        postJson("/auth/reset-password", "{\"token\":\"" + resetLink(user) + "\",\"newPassword\":\"Fresh-Start#2026\"}")
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_history WHERE user_id = ?", Long.class, user.getId())).isEqualTo(1L);
    }

    // ==================== SEC-7 ====================

    @Test
    void signInsRecordChangesAndExportsAreAudited() throws Exception {
        User user = tenant.user(SALES_MANAGER);
        user.setPasswordHash(passwordEncoder.encode("Str0ng!Passw0rd"));
        userRepository.save(user);
        postJson("/auth/login", "{\"email\":\"" + user.getEmail() + "\",\"password\":\"wrong-one\"}").andExpect(status().isUnauthorized());
        String login = postJson("/auth/login", "{\"email\":\"" + user.getEmail() + "\",\"password\":\"Str0ng!Passw0rd\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        postJson("/auth/logout", "{\"refreshToken\":\"" + JsonPath.read(login, "$.data.refreshToken") + "\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT action FROM audit_events WHERE entity_type = 'user' AND entity_id = ? ORDER BY id", String.class, user.getId()))
                .containsSubsequence("USER_SIGN_IN_FAILED", "USER_SIGNED_IN", "USER_SIGNED_OUT");
        assertThat(jdbc.queryForObject("SELECT details FROM audit_events WHERE action = 'USER_SIGN_IN_FAILED' AND entity_id = ?",
                String.class, user.getId())).isEqualTo("invalid_password");

        // Record create / update / delete, with the changed fields (not their values).
        long lead = idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Audit Lead\",\"companyName\":\"Trail Co\",\"industry\":\"trading\",\"source\":\"referral\"}"));
        send(put("/api/leads/" + lead), adminToken, "{\"leadName\":\"Audit Lead\",\"companyName\":\"Trail Co\",\"industry\":\"trading\","
                + "\"source\":\"referral\",\"notes\":\"secret pricing note\"}").andExpect(status().isOk());
        send(delete("/api/leads/" + lead), adminToken, null).andExpect(status().isOk());
        List<String> trail = jdbc.queryForList("SELECT action || ': ' || coalesce(details, '') FROM audit_events WHERE entity_type = 'lead' "
                + "AND entity_id = ? ORDER BY id", String.class, lead);
        assertThat(trail).containsExactly("RECORD_CREATED: Audit Lead", "RECORD_UPDATED: Audit Lead — changed: notes", "RECORD_DELETED: Audit Lead");
        assertThat(String.join(" ", trail)).doesNotContain("secret pricing note");
        send(post("/api/opportunity-stages"), adminToken, "{\"label\":\"Demo\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'RECORD_CREATED' AND entity_type = 'pipeline_stage' "
                + "AND tenant_id = ?", Long.class, tenant.tenant().getId())).isEqualTo(1L);

        // A report download is a data export.
        send(get("/api/reports/export/sales").param("format", "xlsx"), adminToken, null).andExpect(status().isOk());
        send(get("/api/audit-events").param("action", "REPORT_EXPORTED"), adminToken, null)
                .andExpect(jsonPath("$.data.items[0].entityType").value("report"))
                .andExpect(jsonPath("$.data.items[0].details").value(containsString("sales as xlsx")));
    }

    // ==================== INT-9 ====================

    @Test
    void outboundCallsAreLoggedWithoutPersonalData() throws Exception {
        IntegrationCallLog.start(callLog, "twilio", "sms.send").failed(400, "The 'To' number +919876543210 is not valid for buyer@x.test");
        IntegrationCallLog.start(callLog, "smtp", "email.send").ok(null);

        send(get("/api/integration-calls").param("provider", "twilio").param("outcome", "failed"), testTenants.superAdminToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calls[0].operation").value("sms.send"))
                .andExpect(jsonPath("$.data.calls[0].httpStatus").value(400))
                .andExpect(jsonPath("$.data.calls[*].error", everyItem(not(containsString("9876543210")))))
                .andExpect(jsonPath("$.data.calls[*].error", everyItem(not(containsString("buyer@x.test")))))
                .andExpect(jsonPath("$.data.lastDay[*].provider", hasItem("twilio")))
                .andExpect(jsonPath("$.data.lastDay[*].provider", hasItem("smtp")));
        send(get("/api/integration-calls"), adminToken, null).andExpect(status().isForbidden());
    }

    // ==================== helpers ====================

    private String resetLink(User user) throws Exception {
        clearInvocations(mailService);
        postJson("/auth/forgot-password", "{\"email\":\"" + user.getEmail() + "\"}").andExpect(status().isOk());
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendPasswordResetEmail(eq(user.getEmail()), anyString(), raw.capture());
        return raw.getValue();
    }

    private long count(String action, String entityType, long entityId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = ? AND entity_type = ? AND entity_id = ?",
                Long.class, action, entityType, entityId);
    }

    private ResultActions stage(long deal, String body) throws Exception {
        return send(patch("/api/opportunities/" + deal + "/stage"), adminToken, body);
    }

    private ResultActions check(String token, String body) throws Exception {
        return send(post("/api/duplicates/check"), token, body).andExpect(status().isOk());
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
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
