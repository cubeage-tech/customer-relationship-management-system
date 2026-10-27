package com.company.crm.notification;

import com.company.crm.ai.dto.AiDtos.SentimentJudgement;
import com.company.crm.ai.llm.LlmGateway;
import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.communication.integration.TwilioClient;
import com.company.crm.communication.service.CommunicationService;
import com.company.crm.communication.service.CommunicationService.InboundMessage;
import com.company.crm.integration.health.IntegrationHealthService;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NOT-2 (WhatsApp), NOT-3 (SMS escalation setting), NOT-5 (in-app alerts), NOT-6 (channel
 * settings), NOT-7 (consent), NOT-8 (delivery log) and INT-8 (integration credential failures).
 */
class NotificationsAndConsentIntegrationTest extends PostgresIntegrationTest {

    @MockitoBean private LlmGateway llm;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CommunicationService communicationService;
    @Autowired private IntegrationHealthService integrationHealth;

    private TestTenant tenant;
    private String adminToken;
    private String phone;
    private String email;
    private long customerId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Notify Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        long n = System.nanoTime();
        phone = "+919" + String.format("%09d", n % 1_000_000_000L);
        email = "buyer" + n + "@buyer.test";
        customerId = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Buyer " + n + "\",\"industry\":\"trading\",\"email\":\""
                + email + "\",\"phone\":\"" + phone + "\",\"ownerId\":" + tenant.user(SALES_EXECUTIVE).getId() + "}"));
        when(twilioClient.isWhatsAppEnabled()).thenReturn(true);
        when(twilioClient.isSmsEnabled()).thenReturn(true);
        when(twilioClient.sendWhatsApp(anyString(), anyString())).thenAnswer(i -> new TwilioClient.SendResult("WA" + System.nanoTime(), "queued"));
        when(twilioClient.sendSms(anyString(), anyString())).thenAnswer(i -> new TwilioClient.SendResult("SM" + System.nanoTime(), "queued"));
    }

    // ==================== NOT-6 ====================

    @Test
    void adminChoosesTheChannelsOfEachEvent() throws Exception {
        send(get("/api/notification-settings"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.event == 'quotation_sent')].channels[?(@.channel == 'email')].enabled").value(contains(true)))
                .andExpect(jsonPath("$.data[?(@.event == 'quotation_sent')].channels[?(@.channel == 'whatsapp')].enabled").value(contains(false)))
                .andExpect(jsonPath("$.data[?(@.event == 'ticket_sla_escalated')].channels[*].channel").value(org.hamcrest.Matchers.contains("in_app", "email", "sms")))
                .andExpect(jsonPath("$.data[?(@.event == 'ai_churn_risk')].channels[?(@.channel == 'in_app')].enabled").value(contains(false)));
        send(get("/api/notification-settings"), tenant.token(SALES_MANAGER), null).andExpect(status().isForbidden());

        // switch the quotation email off: sending a quotation emails nobody
        send(put("/api/notification-settings"), adminToken, "[{\"event\":\"quotation_sent\",\"channel\":\"email\",\"enabled\":false},"
                + "{\"event\":\"ticket_sla_escalated\",\"channel\":\"sms\",\"enabled\":true}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.event == 'quotation_sent')].channels[?(@.channel == 'email')].enabled").value(contains(false)))
                .andExpect(jsonPath("$.data[?(@.event == 'ticket_sla_escalated')].channels[?(@.channel == 'sms')].recipient").value(org.hamcrest.Matchers.contains("internal")));
        sendQuotation(0);
        verify(mailService, never()).sendCustomerEmail(eq(email), ArgumentMatchers.startsWith("Quotation"), anyString(), anyString());
        send(put("/api/notification-settings"), adminToken, "[{\"event\":\"quotation_sent\",\"channel\":\"in_app\",\"enabled\":true}]")
                .andExpect(status().isBadRequest());
        send(put("/api/notification-settings"), adminToken, "[{\"event\":\"integration_auth_failed\",\"channel\":\"email\",\"enabled\":false}]")
                .andExpect(status().isBadRequest());
    }

    // ==================== NOT-2 / NOT-7 / NOT-8 ====================

    @Test
    void whatsAppUpdatesOnlyReachCustomersWhoOptedIn() throws Exception {
        send(put("/api/notification-settings"), adminToken, "[{\"event\":\"quotation_sent\",\"channel\":\"whatsapp\",\"enabled\":true}]")
                .andExpect(status().isOk());

        sendQuotation(0);
        verify(twilioClient, never()).sendWhatsApp(anyString(), anyString());
        send(get("/api/notification-deliveries").param("channel", "whatsapp"), adminToken, null)
                .andExpect(jsonPath("$.data[0].status").value("skipped"))
                .andExpect(jsonPath("$.data[0].detail").value(containsString("opted in")));

        send(get("/api/customers/" + customerId + "/communication-preferences"), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(jsonPath("$.data.whatsapp").value(false)).andExpect(jsonPath("$.data.sms").value(true));
        send(put("/api/customers/" + customerId + "/communication-preferences"), tenant.token(FINANCE_APPROVER), "{\"whatsapp\":true}")
                .andExpect(status().isForbidden());
        send(put("/api/customers/" + customerId + "/communication-preferences"), tenant.token(SALES_EXECUTIVE), "{\"whatsapp\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.whatsapp").value(true)).andExpect(jsonPath("$.data.source").value("staff"));

        sendQuotation(0);
        verify(twilioClient).sendWhatsApp(eq(phone), ArgumentMatchers.contains("is ready. View it here"));
        send(get("/api/notification-deliveries").param("channel", "whatsapp"), adminToken, null)
                .andExpect(jsonPath("$.data[0].status").value("queued")) // accepted by Twilio; delivery reports update it
                .andExpect(jsonPath("$.data[0].recipientType").value("customer"))
                .andExpect(jsonPath("$.data[0].communicationId").isNumber());
        // the quotation email is in the log too, linked to its timeline entry
        send(get("/api/notification-deliveries").param("event", "quotation_sent").param("channel", "email"), adminToken, null)
                .andExpect(jsonPath("$.data.length()").value(2));
        Integer audits = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'CUSTOMER_CONSENT_CHANGED' AND entity_id = ?",
                Integer.class, customerId);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void replyingStopOptsOutOfSmsAndCampaigns() throws Exception {
        communicationService.recordInbound(new InboundMessage(CommunicationChannel.SMS, phone, null, "Stop", "SMin" + System.nanoTime(), List.of(), null));
        send(get("/api/customers/" + customerId + "/communication-preferences"), adminToken, null)
                .andExpect(jsonPath("$.data.sms").value(false)).andExpect(jsonPath("$.data.source").value("sms_stop"));

        // manual SMS refused, automated SMS alert skipped
        send(post("/api/communications/sms"), adminToken, "{\"customerId\":" + customerId + ",\"to\":\"" + phone + "\",\"body\":\"Hi\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CUSTOMER_OPTED_OUT"));
        send(put("/api/notification-settings"), adminToken, "[{\"event\":\"quotation_sent\",\"channel\":\"sms\",\"enabled\":true,\"recipient\":\"customer\"}]")
                .andExpect(status().isOk());
        sendQuotation(0);
        verify(twilioClient, never()).sendSms(eq(phone), anyString());
        send(get("/api/notification-deliveries").param("channel", "sms"), adminToken, null)
                .andExpect(jsonPath("$.data[0].status").value("skipped"));

        // an SMS campaign skips them
        long segment = idOf(send(post("/api/segments"), tenant.token(MARKETING_EXECUTIVE), "{\"name\":\"All trading\",\"industries\":[\"trading\"]}"));
        long campaign = idOf(send(post("/api/campaigns"), tenant.token(MARKETING_EXECUTIVE), "{\"name\":\"Offer\",\"channel\":\"sms\",\"segmentId\":" + segment
                + ",\"messageBody\":\"10% off this week\"}"));
        send(patch("/api/campaigns/" + campaign + "/launch"), tenant.token(MARKETING_EXECUTIVE), null).andExpect(status().isOk());
        String reason = jdbc.queryForObject("SELECT error_message FROM campaign_recipients WHERE campaign_id = ? AND customer_id = ?",
                String.class, campaign, customerId);
        assertThat(reason).isEqualTo("Opted out of SMS");

        communicationService.recordInbound(new InboundMessage(CommunicationChannel.SMS, phone, null, "START", "SMin" + System.nanoTime(), List.of(), null));
        send(get("/api/customers/" + customerId + "/communication-preferences"), adminToken, null).andExpect(jsonPath("$.data.sms").value(true));
    }

    @Test
    void whatsAppCampaignsGoOnlyToOptedInCustomers() throws Exception {
        long segment = idOf(send(post("/api/segments"), tenant.token(MARKETING_EXECUTIVE), "{\"name\":\"WA\",\"industries\":[\"trading\"]}"));
        long campaign = idOf(send(post("/api/campaigns"), tenant.token(MARKETING_EXECUTIVE), "{\"name\":\"WA promo\",\"channel\":\"whatsapp\",\"segmentId\":"
                + segment + ",\"messageBody\":\"Diwali offer: 15% off\"}"));
        send(patch("/api/campaigns/" + campaign + "/launch"), tenant.token(MARKETING_EXECUTIVE), null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT error_message FROM campaign_recipients WHERE campaign_id = ? AND customer_id = ?",
                String.class, campaign, customerId)).isEqualTo("Hasn't opted in to WhatsApp");
    }

    // ==================== NOT-5 ====================

    @Test
    void approversHearAboutDiscountsThatNeedApproval() throws Exception {
        String exec = tenant.token(SALES_EXECUTIVE);
        idOf(send(post("/api/quotations"), exec, quotationJson(40)));
        for (var role : List.of(ADMIN, SALES_MANAGER, FINANCE_APPROVER)) {
            send(get("/api/notifications"), tenant.token(role), null)
                    .andExpect(jsonPath("$.data.items[?(@.type == 'discount_approval')].title").value(hasItem(org.hamcrest.Matchers.startsWith("Discount approval needed"))));
        }
        send(get("/api/notifications"), exec, null).andExpect(jsonPath("$.data.items[?(@.type == 'discount_approval')]").isEmpty());

        // a role whose approval permission is switched off (ROLE-8) isn't told
        send(put("/api/role-permissions/sales_manager"), adminToken, "{\"revoked\":[\"quotations.approve\"]}").andExpect(status().isOk());
        jdbc.update("UPDATE notifications SET read_at = now() WHERE user_id = ?", tenant.user(SALES_MANAGER).getId());
        idOf(send(post("/api/quotations"), exec, quotationJson(50)));
        send(get("/api/notifications"), tenant.token(SALES_MANAGER), null).andExpect(jsonPath("$.data.unread").value(0));

        // in-app deliveries are logged and read once opened
        String inbox = send(get("/api/notifications"), tenant.token(FINANCE_APPROVER), null).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(inbox, "$.data.items[0].id")).longValue();
        send(patch("/api/notifications/" + id + "/read"), tenant.token(FINANCE_APPROVER), null).andExpect(status().isOk());
        send(get("/api/notification-deliveries").param("channel", "in_app").param("status", "read"), adminToken, null)
                .andExpect(jsonPath("$.data[*].recipientId").value(hasItem(tenant.user(FINANCE_APPROVER).getId().intValue())));
    }

    @Test
    void anUnhappyCustomerMessageAlertsTheOwnerWhenSwitchedOn() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.structured(anyString(), anyString(), eq(SentimentJudgement.class)))
                .thenReturn(Optional.of(new SentimentJudgement("negative", -0.9, "high", "They threaten to cancel the contract")));
        communicationService.recordInbound(new InboundMessage(CommunicationChannel.EMAIL, email, "Re: service", "Third failure this month. Call me on "
                + phone + " or we cancel.", "<in" + System.nanoTime() + "@buyer>", List.of(), null));
        verify(llm, after(500).never()).structured(anyString(), anyString(), eq(SentimentJudgement.class)); // off by default

        send(put("/api/notification-settings"), adminToken, "[{\"event\":\"ai_churn_risk\",\"channel\":\"in_app\",\"enabled\":true}]").andExpect(status().isOk());
        communicationService.recordInbound(new InboundMessage(CommunicationChannel.EMAIL, email, "Re: service", "Third failure this month. Call me on "
                + phone + " or we cancel.", "<in" + System.nanoTime() + "@buyer>", List.of(), null));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm, timeout(5000)).structured(anyString(), prompt.capture(), eq(SentimentJudgement.class));
        assertThat(prompt.getValue()).contains("we cancel").doesNotContain(phone.substring(3)).contains("[number]");

        String owner = tenant.token(SALES_EXECUTIVE);
        for (int i = 0; i < 50 && !send(get("/api/notifications"), owner, null).andReturn().getResponse().getContentAsString().contains("ai_alert"); i++) {
            Thread.sleep(100);
        }
        send(get("/api/notifications"), owner, null)
                .andExpect(jsonPath("$.data.items[?(@.type == 'ai_alert')].body").value(hasItem(containsString("cancel the contract"))))
                .andExpect(jsonPath("$.data.items[?(@.type == 'ai_alert')].entityType").value(hasItem("customer")));
    }

    // ==================== INT-8 ====================

    @Test
    void aRejectedCredentialAlertsThePlatformAdminsOnce() throws Exception {
        String platform = testTenants.superAdminToken();
        jdbc.update("DELETE FROM integration_health");
        integrationHealth.authFailed(IntegrationHealthService.TWILIO, "Twilio answered HTTP 401: Authenticate");
        integrationHealth.authFailed(IntegrationHealthService.TWILIO, "Twilio answered HTTP 401: Authenticate");

        send(get("/api/notifications"), platform, null)
                .andExpect(jsonPath("$.data.items[?(@.type == 'integration')].title").value(org.hamcrest.Matchers.contains("Re-authorise SMS and WhatsApp (Twilio)")));
        verify(mailService, atLeastOnce()).sendCalendarEmail(anyString(), eq("Re-authorise SMS and WhatsApp (Twilio)"),
                ArgumentMatchers.contains("TWILIO_AUTH_TOKEN"), isNull());
        send(get("/api/integration-health"), platform, null)
                .andExpect(jsonPath("$.data[?(@.integration == 'twilio')].status").value(org.hamcrest.Matchers.contains("auth_failed")))
                .andExpect(jsonPath("$.data[?(@.integration == 'smtp')].status").value(org.hamcrest.Matchers.contains("unknown")));
        send(get("/api/integration-health"), adminToken, null).andExpect(status().isForbidden());
        send(get("/api/notification-settings"), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.event == 'quotation_sent')].channels[?(@.channel == 'sms')].note").value(hasItem(containsString("stopped accepting"))));

        integrationHealth.ok(IntegrationHealthService.TWILIO);
        send(get("/api/integration-health"), platform, null).andExpect(jsonPath("$.data[?(@.integration == 'twilio')].status").value(org.hamcrest.Matchers.contains("ok")));
    }

    // ==================== helpers ====================

    private String quotationJson(int discount) {
        return "{\"customerId\":" + customerId + ",\"lineItems\":[{\"productName\":\"Crane\",\"quantity\":1,\"unitPrice\":1000,\"discountPercent\":"
                + discount + "}]}";
    }

    private void sendQuotation(int discount) throws Exception {
        long quotationId = idOf(send(post("/api/quotations"), adminToken, quotationJson(discount)));
        send(patch("/api/quotations/" + quotationId + "/send"), adminToken, null).andExpect(status().isOk());
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
