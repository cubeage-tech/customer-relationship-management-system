package com.company.crm.dataprotection;

import com.company.crm.ai.llm.LlmGateway;
import com.company.crm.ai.service.AiQuotationDraftService.DraftPlan;
import com.company.crm.ai.service.AiQuotationDraftService.PlannedLine;
import com.company.crm.ai.dto.AiDtos.ChatPlan;
import com.company.crm.ai.dto.AiDtos.ChatWording;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-4 (request IDs), API-G01 (refresh tokens), API-G02 (customer history), API-G08 (campaign
 * performance), NAV-2 (customer response, AI draft), SEC-5/6 (AI data controls), SEC-8 (export and
 * erasure), BIZ-2 (editions) and NAV-M11 (integration status).
 */
class ApiPrivacyEditionsIntegrationTest extends PostgresIntegrationTest {

    @MockitoBean private LlmGateway llm;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;

    private TestTenant tenant;
    private String adminToken;
    private User exec;
    private long customerId;
    private String email;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Privacy Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        exec = tenant.user(SALES_EXECUTIVE);
        long n = System.nanoTime();
        email = "owner" + n + "@buyer.test";
        customerId = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Buyer " + n + "\",\"industry\":\"trading\",\"email\":\""
                + email + "\",\"notes\":\"Prefers calls after 5pm\",\"ownerId\":" + exec.getId() + "}"));
        when(llm.isConfigured()).thenReturn(true);
    }

    // ==================== API-4 ====================

    @Test
    void everyErrorCarriesTheRequestId() throws Exception {
        send(get("/api/customers/999999999"), adminToken, null)
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isString());
        mockMvc.perform(get("/api/customers").header("X-Request-Id", "gateway-abc-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "gateway-abc-123"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.requestId").value("gateway-abc-123"));
        send(get("/api/integration-health"), adminToken, null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.requestId").isString());
        send(get("/api/customers/" + customerId), adminToken, null).andExpect(jsonPath("$.requestId").doesNotExist()); // only on errors
    }

    // ==================== API-G01 ====================

    @Test
    void refreshTokensRotateAndAReusedOneEndsTheSession() throws Exception {
        User u = tenant.user(SALES_MANAGER);
        u.setPasswordHash(passwordEncoder.encode("Str0ng!Passw0rd"));
        userRepository.save(u);
        String login = postJson("/auth/login", "{\"email\":\"" + u.getEmail() + "\",\"password\":\"Str0ng!Passw0rd\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn().getResponse().getContentAsString();
        String first = JsonPath.read(login, "$.data.refreshToken");

        String refreshed = postJson("/auth/refresh", "{\"refreshToken\":\"" + first + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString())
                .andReturn().getResponse().getContentAsString();
        String second = JsonPath.read(refreshed, "$.data.refreshToken");
        assertThat(second).isNotEqualTo(first);
        String access = JsonPath.read(refreshed, "$.data.token");
        send(get("/api/customers"), access, null).andExpect(status().isOk());

        // the first token was used up: presenting it again ends the whole session family
        postJson("/auth/refresh", "{\"refreshToken\":\"" + first + "\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("REFRESH_TOKEN_INVALID"));
        postJson("/auth/refresh", "{\"refreshToken\":\"" + second + "\"}").andExpect(status().isUnauthorized());

        // sign out ends a fresh session's refresh token
        String again = JsonPath.read(postJson("/auth/login", "{\"email\":\"" + u.getEmail() + "\",\"password\":\"Str0ng!Passw0rd\"}")
                .andReturn().getResponse().getContentAsString(), "$.data.refreshToken");
        postJson("/auth/logout", "{\"refreshToken\":\"" + again + "\"}").andExpect(status().isOk());
        postJson("/auth/refresh", "{\"refreshToken\":\"" + again + "\"}").andExpect(status().isUnauthorized());
        postJson("/auth/refresh", "{\"refreshToken\":\"nonsense\"}").andExpect(status().isUnauthorized());
    }

    // ==================== API-G02 ====================

    @Test
    void customerHistoryShowsWhatHappenedNewestFirst() throws Exception {
        idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customerId + ",\"dealValue\":250000,\"productService\":\"Crane\",\"ownerId\":"
                + exec.getId() + "}"));
        long q = idOf(send(post("/api/quotations"), adminToken, quotation(0)));
        send(patch("/api/quotations/" + q + "/send"), adminToken, null).andExpect(status().isOk());
        idOf(send(post("/api/tickets"), adminToken, "{\"customerId\":" + customerId + ",\"subject\":\"Noise\",\"priority\":\"high\"}"));

        send(get("/api/customers/" + customerId + "/history"), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].type").value(hasItems("customer", "deal", "quotation", "message")))
                .andExpect(jsonPath("$.data[*].title").value(hasItems("Deal opened", "Customer added")))
                .andExpect(jsonPath("$.data[?(@.type == 'ticket')]").isEmpty()); // not a service role
        send(get("/api/customers/" + customerId + "/history").param("types", "ticket"), tenant.token(SERVICE_AGENT), null)
                .andExpect(jsonPath("$.data[0].title").value("Service request raised"))
                .andExpect(jsonPath("$.data[?(@.type == 'deal')]").isEmpty());
        TestTenant other = testTenants.tenantWithAllRoles("Other " + System.nanoTime());
        send(get("/api/customers/" + customerId + "/history"), other.token(ADMIN), null).andExpect(status().isNotFound());
    }

    // ==================== API-G08 ====================

    @Test
    void campaignPerformanceIncludesWhatTheCampaignEarned() throws Exception {
        String marketing = tenant.token(MARKETING_EXECUTIVE);
        long campaign = idOf(send(post("/api/campaigns"), marketing, "{\"name\":\"Expo\",\"channel\":\"event\",\"actualCost\":10000}"));
        long lead = idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Expo lead\",\"companyName\":\"Expo Co\",\"industry\":\"trading\","
                + "\"source\":\"exhibition\",\"campaignId\":" + campaign + "}"));
        long deal = idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customerId + ",\"dealValue\":50000,\"ownerId\":" + exec.getId() + "}"));
        jdbc.update("UPDATE opportunities SET lead_id = ?, stage = 'won' WHERE id = ?", lead, deal);
        jdbc.update("UPDATE leads SET stage = 'converted' WHERE id = ?", lead);

        send(get("/api/campaigns/" + campaign + "/performance"), marketing, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.leadsGenerated").value(1))
                .andExpect(jsonPath("$.data.wonDeals").value(1))
                .andExpect(jsonPath("$.data.attributedRevenue").value(50000.0))
                .andExpect(jsonPath("$.data.costPerLead").value(10000.0))
                .andExpect(jsonPath("$.data.returnOnSpendPercent").value(400.0));
        send(get("/api/campaigns/" + campaign + "/performance"), tenant.token(SALES_EXECUTIVE), null).andExpect(status().isForbidden());
    }

    // ==================== NAV-2 ====================

    @Test
    void theCustomerAnswersFromTheQuotationLink() throws Exception {
        long q = idOf(send(post("/api/quotations"), adminToken, quotation(0)));
        send(patch("/api/quotations/" + q + "/send"), adminToken, null).andExpect(status().isOk());
        String token = jdbc.queryForObject("SELECT view_token FROM quotations WHERE id = ?", String.class, q);

        mockMvc.perform(get("/api/public/quotations/" + token)).andExpect(jsonPath("$.data.canRespond").value(true));
        postJson("/api/public/quotations/" + token + "/respond", "{\"decision\":\"accept\",\"comment\":\"Please deliver by June\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("approved")).andExpect(jsonPath("$.data.canRespond").value(false));
        postJson("/api/public/quotations/" + token + "/respond", "{\"decision\":\"decline\"}").andExpect(status().isConflict());
        postJson("/api/public/quotations/not-a-token/respond", "{\"decision\":\"accept\"}").andExpect(status().isNotFound());

        send(get("/api/quotations/" + q), adminToken, null).andExpect(jsonPath("$.data.customerResponseComment").value("Please deliver by June"));
        send(get("/api/notifications"), adminToken, null)
                .andExpect(jsonPath("$.data.items[?(@.type == 'quotation_response')].title").value(hasItem(containsString("accepted"))));
    }

    @Test
    void theAiDraftsAQuotationFromTheProductList() throws Exception {
        send(post("/api/products"), adminToken, "{\"name\":\"Gear pump\",\"unitPrice\":12000}").andExpect(status().isOk());
        when(llm.structured(anyString(), anyString(), eq(DraftPlan.class))).thenReturn(Optional.of(new DraftPlan(List.of(
                new PlannedLine("gear PUMP", 3, 40, "main unit"), new PlannedLine("Installation", 1, 0, "on site")), "Thank you for your interest.")));

        send(post("/api/ai/quotation-draft"), tenant.token(SALES_EXECUTIVE), "{\"customerId\":" + customerId + ",\"notes\":\"Call " + email + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineItems[0].productName").value("Gear pump"))
                .andExpect(jsonPath("$.data.lineItems[0].unitPrice").value(12000.0))
                .andExpect(jsonPath("$.data.lineItems[0].discountPercent").value(15.0)) // clamped to the approval threshold
                .andExpect(jsonPath("$.data.lineItems[1].fromCatalog").value(false))
                .andExpect(jsonPath("$.data.checks[0]").value(containsString("Installation")));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), prompt.capture(), eq(DraftPlan.class));
        assertThat(prompt.getValue()).contains("Gear pump | 12000").contains("trading").doesNotContain("Prefers calls"); // no notes of the customer

        // SEC-6: excluded customers never reach the AI
        send(put("/api/ai/exclusions/customer/" + customerId), tenant.token(SALES_EXECUTIVE), "{\"excluded\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.excluded").value(true));
        send(post("/api/ai/quotation-draft"), tenant.token(SALES_EXECUTIVE), "{\"customerId\":" + customerId + "}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("AI_EXCLUDED"));
        send(post("/api/ai/generate-email"), tenant.token(SALES_EXECUTIVE), "{\"customerId\":" + customerId + ",\"purpose\":\"follow up\"}")
                .andExpect(status().isForbidden());
    }

    // ==================== SEC-5 / SEC-6 ====================

    @Test
    void theTenantCanSwitchAiOffOrWithholdMessageText() throws Exception {
        send(get("/api/ai/settings"), adminToken, null)
                .andExpect(jsonPath("$.data.settings.aiEnabled").value(true))
                .andExpect(jsonPath("$.data.policy[*].feature").value(hasItem("Draft a quotation")));
        send(put("/api/ai/settings"), tenant.token(SALES_MANAGER), "{\"aiEnabled\":false,\"messageContent\":true}").andExpect(status().isForbidden());

        send(put("/api/ai/settings"), adminToken, "{\"aiEnabled\":true,\"messageContent\":false}").andExpect(status().isOk());
        long message = jdbc.queryForObject("INSERT INTO communication_logs (tenant_id, customer_id, channel, direction, status, body, occurred_at, created_at)"
                + " VALUES (?, ?, 'email', 'inbound', 'received', 'We are unhappy', now(), now()) RETURNING id", Long.class, tenant.tenant().getId(), customerId);
        send(post("/api/ai/sentiment"), adminToken, "{\"communicationId\":" + message + "}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("AI_MESSAGES_WITHHELD"));

        send(put("/api/ai/settings"), adminToken, "{\"aiEnabled\":false,\"messageContent\":false}").andExpect(status().isOk());
        send(post("/api/ai/chat"), tenant.token(SALES_EXECUTIVE), "{\"question\":\"Which leads are hot?\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("AI_DISABLED"));
        send(get("/api/ai/status"), tenant.token(SALES_EXECUTIVE), null).andExpect(jsonPath("$.data.llmConfigured").value(false));
        verify(llm, never()).structured(anyString(), anyString(), any());
        Integer audits = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'AI_SETTINGS_CHANGED' AND tenant_id = ?",
                Integer.class, tenant.tenant().getId());
        assertThat(audits).isEqualTo(2);
    }

    @Test
    void chatNeverNamesExcludedRecordsToTheAi() throws Exception {
        long lead = idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Secret Lead\",\"companyName\":\"Hidden Co\",\"industry\":\"trading\","
                + "\"source\":\"website\",\"ownerId\":" + exec.getId() + "}"));
        send(put("/api/ai/exclusions/lead/" + lead), adminToken, "{\"excluded\":true}").andExpect(status().isOk());
        when(llm.structured(anyString(), anyString(), eq(ChatPlan.class)))
                .thenReturn(Optional.of(new ChatPlan("leads", null, null, null, null, null, null, null, null, null)));
        when(llm.structured(anyString(), anyString(), eq(ChatWording.class))).thenReturn(Optional.of(new ChatWording("One lead.", null)));

        send(post("/api/ai/chat"), adminToken, "{\"question\":\"Show my leads\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[*].label").value(hasItem("Secret Lead"))); // the user still sees it
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), prompt.capture(), eq(ChatWording.class));
        assertThat(prompt.getValue()).doesNotContain("Secret Lead").contains("excluded from AI");
    }

    // ==================== SEC-8 ====================

    @Test
    void personalDataCanBeExportedAndErased() throws Exception {
        send(post("/api/customers/" + customerId + "/contacts"), adminToken, "{\"name\":\"Ravi Kumar\",\"email\":\"ravi" + System.nanoTime() + "@buyer.test\"}")
                .andExpect(status().isOk());
        jdbc.update("INSERT INTO communication_logs (tenant_id, customer_id, channel, direction, status, subject, body, occurred_at, created_at)"
                + " VALUES (?, ?, 'email', 'outbound', 'sent', 'Hello Ravi', 'Dear Ravi, about your order', now(), now())", tenant.tenant().getId(), customerId);

        long export = idOf(send(post("/api/data-requests"), adminToken, "{\"subjectType\":\"customer\",\"subjectId\":" + customerId + ",\"requestType\":\"export\"}"));
        send(post("/api/data-requests"), tenant.token(SALES_MANAGER), "{\"subjectType\":\"customer\",\"subjectId\":" + customerId + ",\"requestType\":\"export\"}")
                .andExpect(status().isForbidden());
        String file = send(get("/api/data-requests/" + export + "/export"), adminToken, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(file).contains(email).contains("Ravi Kumar").contains("Dear Ravi, about your order").contains("Prefers calls after 5pm");
        send(get("/api/data-requests"), adminToken, null).andExpect(jsonPath("$.data[0].status").value("completed"))
                .andExpect(jsonPath("$.data[0].dueAt").isString());

        long erase = idOf(send(post("/api/data-requests"), adminToken, "{\"subjectType\":\"customer\",\"subjectId\":" + customerId + ",\"requestType\":\"erasure\"}"));
        send(post("/api/data-requests/" + erase + "/erase"), adminToken, "{\"confirm\":\"yes\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("CONFIRMATION_REQUIRED"));
        send(post("/api/data-requests/" + erase + "/erase"), adminToken, "{\"confirm\":\"ERASE\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.subjectName").value("Erased customer #" + customerId));

        send(get("/api/customers/" + customerId), adminToken, null)
                .andExpect(jsonPath("$.data.companyName").value("Erased customer #" + customerId))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.contacts.length()").value(0));
        assertThat(jdbc.queryForList("SELECT body FROM communication_logs WHERE customer_id = ?", String.class, customerId)).containsOnly("[erased]");
        send(post("/api/data-requests/" + erase + "/erase"), adminToken, "{\"confirm\":\"ERASE\"}").andExpect(status().isBadRequest());
    }

    // ==================== BIZ-2 ====================

    @Test
    void anEditionAddsItsIndustryFieldsAndStagesOnce() throws Exception {
        send(get("/api/editions"), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(jsonPath("$.data.current").value("standard"))
                .andExpect(jsonPath("$.data.editions[*].key").value(hasItems("manufacturing", "real_estate", "healthcare", "industrial_sales")));
        send(put("/api/editions/current"), adminToken, "{\"edition\":\"real_estate\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fieldsAdded").value(greaterThan(0))).andExpect(jsonPath("$.data.stagesAdded").value(2));
        send(put("/api/editions/current"), adminToken, "{\"edition\":\"real_estate\"}")
                .andExpect(jsonPath("$.data.fieldsAdded").value(0)).andExpect(jsonPath("$.data.stagesAdded").value(0));
        send(get("/api/opportunity-stages"), adminToken, null).andExpect(jsonPath("$.data[*].label").value(hasItems("Site visit", "Booking")));
        send(get("/api/custom-fields").param("entityType", "lead"), adminToken, null).andExpect(jsonPath("$.data[*].key").value(hasItem("budget_range")));
        send(get("/api/editions"), adminToken, null).andExpect(jsonPath("$.data.current").value("real_estate"));
        send(put("/api/editions/current"), tenant.token(SALES_MANAGER), "{\"edition\":\"healthcare\"}").andExpect(status().isForbidden());
        send(put("/api/editions/current"), adminToken, "{\"edition\":\"space\"}").andExpect(status().isBadRequest());
    }

    // ==================== NAV-M11 ====================

    @Test
    void theAdminSeesWhichIntegrationsAreSetUp() throws Exception {
        send(get("/api/integrations/status"), adminToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[*].key").value(hasItems("email", "sms", "whatsapp", "push", "ai", "payments", "erp")));
        send(get("/api/integrations/status"), tenant.token(SALES_MANAGER), null).andExpect(status().isForbidden());
    }

    // ==================== helpers ====================

    private String quotation(int discount) {
        return "{\"customerId\":" + customerId + ",\"lineItems\":[{\"productName\":\"Crane\",\"quantity\":1,\"unitPrice\":1000,\"discountPercent\":" + discount + "}]}";
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
