package com.company.crm.ai;

import com.company.crm.ai.dto.AiDtos.ChatPlan;
import com.company.crm.ai.dto.AiDtos.ChatWording;
import com.company.crm.ai.dto.AiDtos.GeneratedText;
import com.company.crm.ai.dto.AiDtos.SentimentJudgement;
import com.company.crm.ai.llm.LlmGateway;
import com.company.crm.integration.controller.IntegrationsController;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-G10 (Dashboard & Reports paths), API-G11 (AI services) and API-G12 (Integrations: payment,
 * WhatsApp webhook, generic ERP sync). The language model is mocked — no external calls.
 */
class AiAndIntegrationsIntegrationTest extends PostgresIntegrationTest {

    @MockitoBean private LlmGateway llm;
    @Autowired private JdbcTemplate jdbc;

    private TestTenant tenant;
    private String adminToken;
    private User exec;

    @BeforeEach
    void setUp() {
        tenant = testTenants.tenantWithAllRoles("AI Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        exec = tenant.user(SALES_EXECUTIVE);
        when(llm.isConfigured()).thenReturn(true);
    }

    // ==================== API-G10 ====================

    @Test
    void dashboardAndReportsAnswerAtTheSrsPaths() throws Exception {
        lead("Asha", "trading", "referral", exec);
        send(get("/api/dashboard"), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current.totalLeads").value(1));
        send(get("/api/reports/sales"), adminToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.byStage").isArray());
        send(get("/api/reports/revenue"), tenant.token(FINANCE_APPROVER), null).andExpect(status().isOk());
        send(get("/api/reports/revenue"), tenant.token(SALES_EXECUTIVE), null).andExpect(status().isForbidden());
        send(get("/api/analytics/sales"), adminToken, null).andExpect(status().isOk()); // existing path unchanged
    }

    // ==================== API-G11 ====================

    @Test
    void leadScoresUseTheTenantsHistoryAndStayInScope() throws Exception {
        // History: trading referrals convert well, software exhibition leads don't.
        for (int i = 0; i < 4; i++) {
            long l = lead("Hist" + i, "trading", "referral", exec);
            jdbc.update("UPDATE leads SET stage = 'converted' WHERE id = ?", l);
            lead("Cold" + i, "software", "exhibition", exec);
        }
        long hot = lead("Hot", "trading", "referral", exec);
        jdbc.update("UPDATE leads SET stage = 'negotiation' WHERE id = ?", hot);
        for (int i = 0; i < 4; i++) {
            jdbc.update("INSERT INTO communication_logs (tenant_id, lead_id, channel, direction, status, occurred_at, created_at)"
                    + " VALUES (?, ?, 'call', 'outbound', 'logged', now(), now())", tenant.tenant().getId(), hot);
        }
        long cold = lead("Cold", "software", "exhibition", exec);

        String scores = send(get("/api/ai/lead-score"), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ordered = JsonPath.read(scores, "$.data[*].score");
        assertThat(ordered).isSortedAccordingTo((a, b) -> b - a);
        int hotScore = JsonPath.<List<Integer>>read(scores, "$.data[?(@.leadId == " + hot + ")].score").get(0);
        int coldScore = JsonPath.<List<Integer>>read(scores, "$.data[?(@.leadId == " + cold + ")].score").get(0);
        assertThat(hotScore).isGreaterThan(coldScore).isBetween(0, 100);
        send(get("/api/ai/lead-score").param("leadId", String.valueOf(hot)), adminToken, null)
                .andExpect(jsonPath("$.data[0].label").value("High"))
                .andExpect(jsonPath("$.data[0].factors.length()").value(5))
                .andExpect(jsonPath("$.data[0].factors[?(@.name == 'engagement')].detail").value(hasItem(containsString("4 message(s)"))));
        send(get("/api/ai/lead-score").param("minScore", "101"), tenant.token(SALES_EXECUTIVE), null).andExpect(jsonPath("$.data.length()").value(0));

        User otherExec = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        send(get("/api/ai/lead-score").param("leadId", String.valueOf(hot)), testTenants.tokenFor(otherExec), null).andExpect(status().isForbidden());
        send(get("/api/ai/lead-score"), tenant.token(SERVICE_AGENT), null).andExpect(status().isForbidden());
    }

    @Test
    void forecastWeighsTheOpenPipelineByStage() throws Exception {
        long customer = customer("Pipe Co", exec);
        LocalDate nextMonth = LocalDate.now().withDayOfMonth(1).plusMonths(1).plusDays(9);
        long early = deal(customer, 10000, nextMonth);
        long late = deal(customer, 10000, nextMonth);
        jdbc.update("UPDATE opportunities SET stage = 'negotiation' WHERE id = ?", late);
        deal(customer, 50000, nextMonth.plusMonths(3)); // outside next month
        String body = send(get("/api/ai/forecast"), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("next_month"))
                .andExpect(jsonPath("$.data.deals.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        double pEarly = ((Number) JsonPath.<List<Object>>read(body, "$.data.deals[?(@.opportunityId == " + early + ")].probability").get(0)).doubleValue();
        double pLate = ((Number) JsonPath.<List<Object>>read(body, "$.data.deals[?(@.opportunityId == " + late + ")].probability").get(0)).doubleValue();
        assertThat(pLate).isGreaterThan(pEarly);
        double expected = ((Number) JsonPath.read(body, "$.data.expectedRevenue")).doubleValue();
        assertThat(expected).isCloseTo(10000 * (pEarly + pLate), org.assertj.core.data.Offset.offset(5.0));
        send(get("/api/ai/forecast").param("period", "next_decade"), adminToken, null).andExpect(status().isBadRequest());
        send(get("/api/ai/forecast"), tenant.token(SERVICE_AGENT), null).andExpect(status().isForbidden());
    }

    @Test
    void chatChoosesAQueryAndAnswersWithinTheUsersScope() throws Exception {
        long dormant = customer("Dormant Ltd", exec);
        long active = customer("Busy Ltd", exec);
        long wonDeal = deal(active, 5000, null);
        send(patch("/api/opportunities/" + wonDeal + "/stage"), adminToken, "{\"stage\":\"won\",\"force\":true}").andExpect(status().isOk());

        when(llm.structured(eq(com.company.crm.ai.service.AiAssistantService.PLAN_SYSTEM), anyString(), eq(ChatPlan.class)))
                .thenReturn(Optional.of(new ChatPlan("inactive_customers", 180, null, null, null, null, null, null)));
        when(llm.structured(eq(com.company.crm.ai.service.AiAssistantService.WORDING_SYSTEM), anyString(), eq(ChatWording.class)))
                .thenReturn(Optional.of(new ChatWording("1 customer hasn't purchased in 6 months.", "Send a reactivation campaign")));

        send(post("/api/ai/chat"), tenant.token(SALES_EXECUTIVE), "{\"question\":\"Show customers who haven't purchased in 6 months\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("inactive_customers"))
                .andExpect(jsonPath("$.data.matchCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].id").value(dormant))
                .andExpect(jsonPath("$.data.recommendedAction").value("Send a reactivation campaign"));

        // A service agent asking about deals gets no data, whatever the model chose.
        when(llm.structured(eq(com.company.crm.ai.service.AiAssistantService.PLAN_SYSTEM), anyString(), eq(ChatPlan.class)))
                .thenReturn(Optional.of(new ChatPlan("opportunities", null, null, null, null, null, null, BigDecimal.ONE)));
        send(post("/api/ai/chat"), tenant.token(SERVICE_AGENT), "{\"question\":\"Biggest deals?\"}")
                .andExpect(jsonPath("$.data.matchCount").value(0))
                .andExpect(jsonPath("$.data.answer").value(containsString("doesn't have access")));
        send(post("/api/ai/chat"), adminToken, "{\"question\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void messagesAreDraftedFromBusinessContextOnly() throws Exception {
        long customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Draft Co\",\"industry\":\"trading\","
                + "\"email\":\"secret.buyer@draft.test\",\"phone\":\"+919811122233\",\"ownerId\":" + exec.getId() + "}"));
        when(llm.structured(anyString(), anyString(), eq(GeneratedText.class)))
                .thenReturn(Optional.of(new GeneratedText("Following up", "Hi, just checking in. — AI Co")));
        send(post("/api/ai/generate-email"), tenant.token(SALES_EXECUTIVE), "{\"customerId\":" + customer + ",\"purpose\":\"Follow up on the demo\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("Following up"))
                .andExpect(jsonPath("$.data.channel").value("email"));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), prompt.capture(), eq(GeneratedText.class));
        assertThat(prompt.getValue()).contains("Draft Co", "Follow up on the demo").doesNotContain("secret.buyer", "9811122233");

        send(post("/api/ai/generate-email"), tenant.token(SALES_EXECUTIVE), "{\"customerId\":" + customer + ",\"channel\":\"sms\",\"purpose\":\"Remind\"}")
                .andExpect(jsonPath("$.data.subject").doesNotExist());
        send(post("/api/ai/generate-email"), adminToken, "{\"customerId\":" + customer + ",\"leadId\":1,\"purpose\":\"x\"}").andExpect(status().isBadRequest());
        send(post("/api/ai/generate-email"), adminToken, "{\"customerId\":" + customer + ",\"channel\":\"fax\",\"purpose\":\"x\"}").andExpect(status().isBadRequest());
        User otherExec = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        send(post("/api/ai/generate-email"), testTenants.tokenFor(otherExec), "{\"customerId\":" + customer + ",\"purpose\":\"x\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void sentimentIsJudgedForTextOrALoggedMessage() throws Exception {
        long customer = customer("Upset Co", exec);
        jdbc.update("INSERT INTO communication_logs (tenant_id, customer_id, channel, direction, status, subject, body, occurred_at, created_at)"
                + " VALUES (?, ?, 'email', 'inbound', 'received', 'Third failure', 'We will cancel if this happens again', now(), now())",
                tenant.tenant().getId(), customer);
        long message = jdbc.queryForObject("SELECT max(id) FROM communication_logs WHERE customer_id = ?", Long.class, customer);
        when(llm.structured(anyString(), anyString(), eq(SentimentJudgement.class)))
                .thenReturn(Optional.of(new SentimentJudgement("negative", -1.7, "high", "Threatens to cancel")));
        send(post("/api/ai/sentiment"), tenant.token(SALES_EXECUTIVE), "{\"communicationId\":" + message + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentiment").value("negative"))
                .andExpect(jsonPath("$.data.score").value(-1.0)) // clamped
                .andExpect(jsonPath("$.data.churnRisk").value("high"));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), prompt.capture(), eq(SentimentJudgement.class));
        assertThat(prompt.getValue()).contains("We will cancel");
        send(post("/api/ai/sentiment"), tenant.token(SALES_EXECUTIVE), "{\"text\":\"  \"}").andExpect(status().isBadRequest());
        User otherExec = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        send(post("/api/ai/sentiment"), testTenants.tokenFor(otherExec), "{\"communicationId\":" + message + "}").andExpect(status().isForbidden());

        when(llm.structured(anyString(), anyString(), eq(SentimentJudgement.class))).thenReturn(Optional.empty());
        send(post("/api/ai/sentiment"), adminToken, "{\"text\":\"ok\"}").andExpect(status().isUnprocessableEntity());
    }

    // ==================== API-G12 ====================

    @Test
    void erpSyncsThroughATenantApiKey() throws Exception {
        long customer = customer("Ledger Buyer", exec);
        send(post("/api/integration-keys"), tenant.token(SALES_MANAGER), "{\"name\":\"Tally\"}").andExpect(status().isForbidden());
        String created = send(post("/api/integration-keys"), adminToken, "{\"name\":\"Tally\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(created, "$.data.apiKey");
        long keyId = ((Number) JsonPath.read(created, "$.data.key.id")).longValue();
        assertThat(key).startsWith("crm_erp_");
        send(get("/api/integration-keys"), adminToken, null)
                .andExpect(jsonPath("$.data[0].keyPrefix").value(key.substring(0, 12)))
                .andExpect(jsonPath("$.data[0].apiKey").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM integration_api_keys WHERE key_hash = ?", Long.class, key)).isZero();

        mockMvc.perform(get("/api/integrations/erp/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/integrations/erp/customers").header("X-Api-Key", "crm_erp_wrong")).andExpect(status().isUnauthorized());
        erp(get("/api/integrations/erp/customers"), key, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + customer + ")].companyName").value(hasItem("Ledger Buyer")));
        erp(put("/api/integrations/erp/customers/" + customer + "/erp-code"), key, "{\"erpCode\":\"C-001\"}")
                .andExpect(jsonPath("$.data.erpCode").value("C-001"));
        long other = customer("Second Buyer", exec);
        erp(put("/api/integrations/erp/customers/" + other + "/erp-code"), key, "{\"erpCode\":\"C-001\"}").andExpect(status().isConflict());

        erp(put("/api/integrations/erp/products"), key, "{\"products\":[{\"erpCode\":\"P-1\",\"name\":\"Pump\",\"unitPrice\":1200}]}")
                .andExpect(jsonPath("$.data.created").value(1));
        erp(put("/api/integrations/erp/products"), key, "{\"products\":[{\"erpCode\":\"P-1\",\"name\":\"Pump v2\",\"unitPrice\":1300},"
                + "{\"erpCode\":\"P-2\",\"name\":\"Valve\",\"unitPrice\":80}]}")
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.updated").value(1));
        send(get("/api/products"), adminToken, null).andExpect(jsonPath("$.data[?(@.name == 'Pump v2')].unitPrice").value(hasItem(1300.0)));

        long deal = deal(customer, 4200, null);
        send(patch("/api/opportunities/" + deal + "/stage"), adminToken, "{\"stage\":\"won\",\"force\":true}").andExpect(status().isOk());
        erp(get("/api/integrations/erp/won-deals").param("since", LocalDateTime.now().minusHours(1).withNano(0).toString()), key, null)
                .andExpect(jsonPath("$.data[0].customerErpCode").value("C-001"))
                .andExpect(jsonPath("$.data[0].dealValue").value(4200.0));
        erp(get("/api/integrations/erp/quotations"), key, null).andExpect(status().isOk());

        // Another tenant's key never sees this tenant's data.
        TestTenant otherTenant = testTenants.tenantWithAllRoles("Other ERP " + System.nanoTime());
        String otherKey = JsonPath.read(send(post("/api/integration-keys"), otherTenant.token(ADMIN), "{\"name\":\"SAP\"}")
                .andReturn().getResponse().getContentAsString(), "$.data.apiKey");
        erp(get("/api/integrations/erp/customers"), otherKey, null).andExpect(jsonPath("$.data.length()").value(0));
        erp(put("/api/integrations/erp/customers/" + customer + "/erp-code"), otherKey, "{\"erpCode\":\"X\"}").andExpect(status().isNotFound());

        send(delete("/api/integration-keys/" + keyId), adminToken, null).andExpect(status().isOk());
        erp(get("/api/integrations/erp/customers"), key, null).andExpect(status().isUnauthorized());
        send(get("/api/audit-events").param("entityType", "integration_key"), adminToken, null)
                .andExpect(jsonPath("$.data.items[*].action").value(hasItems("INTEGRATION_KEY_CREATED", "INTEGRATION_KEY_REVOKED")));
    }

    @Test
    void providerWebhooksAnswerAtTheIntegrationPaths() throws Exception {
        long customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Chat Co\",\"industry\":\"trading\",\"phone\":\"+919700011122\"}"));
        mockMvc.perform(post(IntegrationsController.WHATSAPP_WEBHOOK_PATH).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("From", "whatsapp:+919700011122").param("Body", "Hi").param("MessageSid", "SMx1"))
                .andExpect(status().isForbidden()); // bad signature
        when(twilioClient.isValidSignature(eq(IntegrationsController.WHATSAPP_WEBHOOK_PATH), anyMap(), any())).thenReturn(true);
        mockMvc.perform(post(IntegrationsController.WHATSAPP_WEBHOOK_PATH).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("From", "whatsapp:+919700011122").param("Body", "Hi there").param("MessageSid", "SMx2" + System.nanoTime()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM communication_logs WHERE customer_id = ? AND channel = 'whatsapp'", Long.class, customer))
                .isEqualTo(1L);
        int paymentStatus = mockMvc.perform(post("/api/integrations/payment").contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"payment.captured\"}").header("X-Razorpay-Signature", "bad")).andReturn().getResponse().getStatus();
        assertThat(paymentStatus).isNotIn(401, 403); // reachable without a login; the signature decides
    }

    // ==================== Helpers ====================

    private long lead(String name, String industry, String source, User owner) throws Exception {
        return idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"" + name + "\",\"companyName\":\"" + name + " Co\",\"industry\":\""
                + industry + "\",\"source\":\"" + source + "\",\"ownerId\":" + owner.getId() + "}"));
    }

    private long customer(String name, User owner) throws Exception {
        return idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"" + name + "\",\"industry\":\"trading\",\"ownerId\":" + owner.getId() + "}"));
    }

    private long deal(long customerId, int value, LocalDate closing) throws Exception {
        return idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customerId + ",\"dealValue\":" + value
                + ",\"ownerId\":" + exec.getId() + (closing != null ? ",\"expectedClosingDate\":\"" + closing + "\"" : "") + "}"));
    }

    private ResultActions erp(MockHttpServletRequestBuilder request, String key, String body) throws Exception {
        request.header("X-Api-Key", key);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
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
