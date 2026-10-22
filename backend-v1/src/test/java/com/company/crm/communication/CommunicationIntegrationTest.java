package com.company.crm.communication;

import com.company.crm.communication.email.InboundEmailProcessor;
import com.company.crm.communication.integration.TwilioClient;
import com.company.crm.communication.repository.CommunicationRepo;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Properties;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Module 5: DB-E09 log, FR-5.4a calls, FR-5.1 email, FR-5.2 WhatsApp, FR-5.3 SMS alerts, FR-5.6 timeline. */
class CommunicationIntegrationTest extends PostgresIntegrationTest {

    @Autowired private InboundEmailProcessor inboundEmailProcessor;
    @Autowired private CommunicationRepo communicationRepo;

    private TestTenant tenant;
    private String adminToken;
    private long customerId;
    private String phone10;
    private String email;

    @BeforeEach
    void setUp() throws Exception {
        long n = System.nanoTime();
        tenant = testTenants.tenantWithAllRoles("Comms Co " + n);
        adminToken = tenant.token(ADMIN);
        phone10 = "9" + String.format("%09d", n % 1_000_000_000L);
        email = "buyer" + n + "@abc.test";
        customerId = idOf(send(post("/api/customers"), adminToken,
                "{\"companyName\":\"ABC Engineering\",\"industry\":\"automobile\",\"email\":\"" + email + "\",\"phone\":\"" + phone10 + "\"}"));
    }

    @Test
    void callsAreLoggedAndShownNewestFirst_withAccessRules() throws Exception {
        send(post("/api/communications/calls"), adminToken, "{\"customerId\":" + customerId
                + ",\"direction\":\"outbound\",\"outcome\":\"no_answer\",\"occurredAt\":\"2026-01-01T10:00:00\"}").andExpect(status().isOk());
        send(post("/api/communications/calls"), adminToken, "{\"customerId\":" + customerId
                + ",\"direction\":\"inbound\",\"outcome\":\"connected\",\"durationSeconds\":300,\"notes\":\"Discussed crane delivery\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("call"))
                .andExpect(jsonPath("$.data.status").value("logged"));

        timeline().andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].callOutcome").value("connected"))
                .andExpect(jsonPath("$.data[0].callDurationSeconds").value(300))
                .andExpect(jsonPath("$.data[0].body").value("Discussed crane delivery"))
                .andExpect(jsonPath("$.data[0].createdByName").isNotEmpty())
                .andExpect(jsonPath("$.data[1].callOutcome").value("no_answer"));

        // Validation.
        send(post("/api/communications/calls"), adminToken, "{\"customerId\":" + customerId + ",\"direction\":\"outbound\",\"outcome\":\"maybe\"}")
                .andExpect(status().isBadRequest());
        send(post("/api/communications/calls"), adminToken, "{\"direction\":\"outbound\",\"outcome\":\"busy\"}")
                .andExpect(status().isBadRequest());
        // Sales executive cannot touch a customer they don't own; finance can read but not log.
        send(post("/api/communications/calls"), tenant.token(SALES_EXECUTIVE), "{\"customerId\":" + customerId
                + ",\"direction\":\"outbound\",\"outcome\":\"busy\"}").andExpect(status().isForbidden());
        send(get("/api/communications").param("customerId", String.valueOf(customerId)), tenant.token(FINANCE_APPROVER), null).andExpect(status().isOk());
        send(post("/api/communications/calls"), tenant.token(FINANCE_APPROVER), "{\"customerId\":" + customerId
                + ",\"direction\":\"outbound\",\"outcome\":\"busy\"}").andExpect(status().isForbidden());
        // Another tenant sees nothing.
        TestTenant other = testTenants.tenantWithAllRoles("Other Comms " + System.nanoTime());
        send(get("/api/communications").param("customerId", String.valueOf(customerId)), other.token(ADMIN), null).andExpect(status().isNotFound());
    }

    @Test
    void emailIsSentAndLogged_failureIsRecorded_andRepliesAreMatchedBack() throws Exception {
        long sentId = idOf(send(post("/api/communications/email"), adminToken, "{\"customerId\":" + customerId
                + ",\"to\":\"" + email + "\",\"subject\":\"Quotation follow-up\",\"body\":\"Hello\"}"));
        verify(mailService).sendCustomerEmail(eq(email), eq("Quotation follow-up"), eq("Hello"), startsWith("<crm."));
        String messageId = communicationRepo.findById(sentId).orElseThrow().getProviderMessageId();

        doThrow(new MailSendException("SMTP down")).when(mailService).sendCustomerEmail(any(), any(), any(), any());
        send(post("/api/communications/email"), adminToken, "{\"customerId\":" + customerId
                + ",\"to\":\"" + email + "\",\"subject\":\"Second\",\"body\":\"Hi\"}")
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.errorMessage").isNotEmpty());

        // The customer replies (In-Reply-To our Message-ID) — logged once on their timeline.
        MimeMessage reply = mail(email, "Re: Quotation follow-up", "Sounds good", "<reply-" + System.nanoTime() + "@abc.test>");
        reply.setHeader("In-Reply-To", messageId);
        assertThat(inboundEmailProcessor.process(reply)).isTrue();
        assertThat(inboundEmailProcessor.process(reply)).isFalse(); // same Message-ID → not logged twice
        assertThat(inboundEmailProcessor.process(mail("stranger" + System.nanoTime() + "@nowhere.test", "Hi", "?",
                "<x-" + System.nanoTime() + "@n.test>"))).isFalse();

        timeline().andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[?(@.direction == 'inbound')].status").value(org.hamcrest.Matchers.contains("received")))
                .andExpect(jsonPath("$.data[?(@.direction == 'inbound')].subject").value(org.hamcrest.Matchers.contains("Re: Quotation follow-up")));
    }

    @Test
    void whatsAppSendInboundAndDeliveryStatus() throws Exception {
        // Not configured → 503, nothing logged.
        send(post("/api/communications/whatsapp"), adminToken, "{\"customerId\":" + customerId + ",\"to\":\"" + phone10 + "\",\"body\":\"Hi\"}")
                .andExpect(status().isServiceUnavailable());

        when(twilioClient.isWhatsAppEnabled()).thenReturn(true);
        String sid = "SM" + System.nanoTime();
        when(twilioClient.sendWhatsApp(eq("+91" + phone10), eq("Your crane ships Monday"))).thenReturn(new TwilioClient.SendResult(sid, "queued"));
        send(post("/api/communications/whatsapp"), adminToken, "{\"customerId\":" + customerId + ",\"to\":\"" + phone10 + "\",\"body\":\"Your crane ships Monday\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("queued"))
                .andExpect(jsonPath("$.data.counterpart").value("+91" + phone10));

        // Unsigned webhook is rejected.
        mockMvc.perform(post("/api/communications/webhooks/twilio/status").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("MessageSid", sid).param("MessageStatus", "delivered")).andExpect(status().isForbidden());

        when(twilioClient.isValidSignature(anyString(), anyMap(), any())).thenReturn(true);
        mockMvc.perform(post("/api/communications/webhooks/twilio/status").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-Twilio-Signature", "sig").param("MessageSid", sid).param("MessageStatus", "delivered"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/communications/webhooks/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-Twilio-Signature", "sig").param("From", "whatsapp:+91" + phone10).param("Body", "Thanks!")
                .param("MessageSid", "SM-in-" + System.nanoTime()))
                .andExpect(status().isOk());

        timeline().andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].direction").value("inbound"))
                .andExpect(jsonPath("$.data[0].channel").value("whatsapp"))
                .andExpect(jsonPath("$.data[0].body").value("Thanks!"))
                .andExpect(jsonPath("$.data[1].status").value("delivered"));
    }

    @Test
    void smsAlertsFollowTheTenantRules() throws Exception {
        send(get("/api/communications/sms-alerts"), adminToken, null)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].enabled").value(false));
        send(put("/api/communications/sms-alerts"), tenant.token(SALES_MANAGER), "[]").andExpect(status().isForbidden());

        when(twilioClient.isSmsEnabled()).thenReturn(true);
        when(twilioClient.sendSms(anyString(), anyString())).thenReturn(new TwilioClient.SendResult("SM" + System.nanoTime(), "queued"));

        // Disabled: sending a quotation sends no SMS.
        sendQuotation();
        verify(twilioClient, never()).sendSms(anyString(), anyString());

        send(put("/api/communications/sms-alerts"), adminToken,
                "[{\"event\":\"quotation_sent\",\"enabled\":true,\"recipient\":\"customer\"}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.event == 'quotation_sent')].enabled").value(org.hamcrest.Matchers.contains(true)));

        sendQuotation();
        verify(twilioClient).sendSms(eq("+91" + phone10), contains("has been sent to ABC Engineering"));
        timeline().andExpect(jsonPath("$.data[0].channel").value("sms"))
                .andExpect(jsonPath("$.data[0].status").value("queued"));
    }

    @Test
    void leadTimelineWorksBeforeConversion() throws Exception {
        long leadId = idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Ravi\",\"companyName\":\"XYZ Industries\","
                + "\"industry\":\"manufacturing\",\"source\":\"referral\"}"));
        send(post("/api/communications/calls"), adminToken, "{\"leadId\":" + leadId + ",\"direction\":\"outbound\",\"outcome\":\"connected\"}")
                .andExpect(status().isOk());
        send(get("/api/communications").param("leadId", String.valueOf(leadId)), adminToken, null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].leadId").value(leadId));
    }

    private void sendQuotation() throws Exception {
        long quotationId = idOf(send(post("/api/quotations"), adminToken, "{\"customerId\":" + customerId
                + ",\"lineItems\":[{\"productName\":\"Crane\",\"quantity\":1,\"unitPrice\":1000,\"discountPercent\":0}]}"));
        send(patch("/api/quotations/" + quotationId + "/send"), adminToken, null).andExpect(status().isOk());
    }

    private ResultActions timeline() throws Exception {
        return send(get("/api/communications").param("customerId", String.valueOf(customerId)), adminToken, null).andExpect(status().isOk());
    }

    private static MimeMessage mail(String from, String subject, String body, String messageId) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties())) {
            @Override
            protected void updateMessageID() throws jakarta.mail.MessagingException {
                setHeader("Message-ID", messageId);
            }
        };
        message.setFrom(new InternetAddress(from));
        message.setSubject(subject);
        message.setText(body);
        message.saveChanges();
        return message;
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
