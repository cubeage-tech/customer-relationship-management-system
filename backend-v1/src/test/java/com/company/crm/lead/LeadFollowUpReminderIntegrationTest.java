package com.company.crm.lead;

import com.company.crm.communication.integration.TwilioClient;
import com.company.crm.lead.scheduler.FollowUpReminderJob;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static com.company.crm.common.enums.RoleType.ADMIN;
import static com.company.crm.common.enums.RoleType.SALES_EXECUTIVE;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-2.5: owners are reminded once per due follow-up (email, plus the FR-5.3 SMS alert when enabled). */
class LeadFollowUpReminderIntegrationTest extends PostgresIntegrationTest {

    @Autowired private FollowUpReminderJob job;
    @Autowired private JdbcTemplate jdbcTemplate;

    private TestTenant tenant;
    private String adminToken;
    private User owner;

    @BeforeEach
    void setUp() {
        tenant = testTenants.tenantWithAllRoles("Follow Up Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        owner = tenant.user(SALES_EXECUTIVE);
    }

    @Test
    void dueFollowUpsEmailTheOwnerOncePerDate() throws Exception {
        LocalDateTime past = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.MINUTES);
        long due = createLead("Ravi", past);
        createLead("Later", LocalDateTime.now().plusDays(3));
        long converted = createLead("Done", past);
        send(patch("/api/leads/" + converted + "/stage"), adminToken, "{\"stage\":\"converted\",\"force\":true}").andExpect(status().isOk());

        job.remindDueFollowUps();
        job.remindDueFollowUps(); // second run: nothing new

        verify(mailService, times(1)).sendFollowUpReminderEmail(eq(owner.getEmail()), anyString(), eq("Ravi"), anyString(), anyString(), eq(due));
        verify(mailService, never()).sendFollowUpReminderEmail(anyString(), anyString(), eq("Later"), anyString(), anyString(), anyLong());
        verify(mailService, never()).sendFollowUpReminderEmail(anyString(), anyString(), eq("Done"), anyString(), anyString(), anyLong());

        // A new follow-up date gets its own reminder.
        send(put("/api/leads/" + due), adminToken, leadJson("Ravi", past.minusMinutes(30))).andExpect(status().isOk());
        job.remindDueFollowUps();
        verify(mailService, times(2)).sendFollowUpReminderEmail(eq(owner.getEmail()), anyString(), eq("Ravi"), anyString(), anyString(), eq(due));
    }

    @Test
    void followUpDueSmsAlertGoesToTheOwnerWhenEnabled() throws Exception {
        jdbcTemplate.update("UPDATE users SET phone = ? WHERE id = ?", "9876543210", owner.getId());
        when(twilioClient.isSmsEnabled()).thenReturn(true);
        when(twilioClient.sendSms(anyString(), anyString())).thenReturn(new TwilioClient.SendResult("SM" + System.nanoTime(), "queued"));

        // The follow-up alert can only target the lead owner.
        send(put("/api/communications/sms-alerts"), adminToken, "[{\"event\":\"follow_up_due\",\"enabled\":true,\"recipient\":\"customer\"}]")
                .andExpect(status().isBadRequest());
        send(put("/api/communications/sms-alerts"), adminToken, "[{\"event\":\"follow_up_due\",\"enabled\":true,\"recipient\":\"internal\"}]")
                .andExpect(status().isOk());

        long leadId = createLead("Meera", LocalDateTime.now().minusMinutes(5));
        job.remindDueFollowUps();

        verify(twilioClient).sendSms(eq("+919876543210"), contains("follow-up with Meera"));
        send(get("/api/communications").param("leadId", String.valueOf(leadId)), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.channel == 'sms')].status").value(hasItem("queued")));
    }

    private long createLead(String name, LocalDateTime followUp) throws Exception {
        String body = send(post("/api/leads"), adminToken, leadJson(name, followUp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private String leadJson(String name, LocalDateTime followUp) {
        return "{\"leadName\":\"" + name + "\",\"companyName\":\"Acme\",\"industry\":\"trading\",\"source\":\"referral\""
                + ",\"ownerId\":" + owner.getId() + ",\"followUpDate\":\"" + followUp + "\"}";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }
}
