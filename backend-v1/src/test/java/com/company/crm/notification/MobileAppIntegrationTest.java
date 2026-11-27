package com.company.crm.notification;

import com.company.crm.lead.scheduler.FollowUpReminderJob;
import com.company.crm.notification.push.PushRetryService;
import com.company.crm.notification.push.PushSender;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mobile app backend: FR-M.1 visits with purpose/outcome, FR-M.2 assigned records, FR-M.3 meeting
 * reports, FR-M.4 notifications and push, FR-M.5 attendance, FR-M.6 offline sync (idempotent
 * visits, check-in/out at the time recorded offline).
 */
class MobileAppIntegrationTest extends PostgresIntegrationTest {

    @MockitoBean private PushSender pushSender;
    @Autowired private FollowUpReminderJob followUpJob;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PushRetryService pushRetryService;

    private TestTenant tenant;
    private String adminToken;
    private String execToken;
    private User exec;
    private long customer;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Mobile Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        exec = tenant.user(SALES_EXECUTIVE);
        execToken = tenant.token(SALES_EXECUTIVE);
        customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Field Client\",\"industry\":\"trading\",\"ownerId\":" + exec.getId() + "}"));
        when(pushSender.isEnabled()).thenReturn(true);
        when(pushSender.send(anyString(), anyString(), any(), anyMap())).thenReturn(PushSender.Result.SENT);
    }

    @Test
    void visitsRecordPurposeAndOutcomeAndOfflineRetriesDontDuplicate() throws Exception {
        String visit = "{\"customerId\":" + customer + ",\"purpose\":\"Product demo\",\"outcome\":\"Wants a quotation\",\"notes\":\"Met the buyer\","
                + "\"occurredAt\":\"" + LocalDateTime.now().minusHours(3).withNano(0) + "\",\"latitude\":18.52,\"longitude\":73.85,\"clientRef\":\"c0ffee-1\"}";
        long first = idOf(send(post("/api/sales-activities/visits"), execToken, visit));
        send(post("/api/sales-activities/visits"), execToken, visit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(first))
                .andExpect(jsonPath("$.data.purpose").value("Product demo"))
                .andExpect(jsonPath("$.data.outcome").value("Wants a quotation"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_activities WHERE user_id = ?", Long.class, exec.getId())).isEqualTo(1L);
        // Another user's same clientRef is a different visit.
        send(post("/api/sales-activities/visits"), adminToken, visit.replace("\"customerId\":" + customer + ",", "")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_activities WHERE client_ref = 'c0ffee-1'", Long.class)).isEqualTo(2L);
    }

    @Test
    void attendanceRecordedOfflineSyncsAtItsOwnTime() throws Exception {
        LocalDateTime in = LocalDateTime.now().minusHours(3).truncatedTo(ChronoUnit.SECONDS);
        send(post("/api/attendance/check-in"), execToken, "{\"at\":\"" + LocalDateTime.now().plusHours(1).withNano(0) + "\"}").andExpect(status().isBadRequest());
        send(post("/api/attendance/check-in"), execToken, "{\"at\":\"" + LocalDateTime.now().minusDays(3).withNano(0) + "\"}").andExpect(status().isBadRequest());
        send(post("/api/attendance/check-in"), execToken, "{\"at\":\"" + in + "\",\"latitude\":18.5,\"longitude\":73.8}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkInAt").value(containsString(in.toString().substring(0, 16))));
        send(post("/api/attendance/check-out"), execToken, "{\"at\":\"" + in.minusMinutes(5) + "\"}").andExpect(status().isBadRequest());
        send(post("/api/attendance/check-out"), execToken, "{\"at\":\"" + in.plusHours(2) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hours").value(2.0));
        send(get("/api/attendance/me"), execToken, null).andExpect(jsonPath("$.data.current").doesNotExist());
    }

    @Test
    void meetingReportsAreFiledAfterTheMeeting() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        long meeting = idOf(send(post("/api/meetings"), execToken, "{\"customerId\":" + customer + ",\"title\":\"Site visit\",\"startsAt\":\""
                + start + "\",\"endsAt\":\"" + start.plusHours(1) + "\"}"));
        String report = "{\"outcome\":\"positive\",\"notes\":\"Agreed on scope\",\"nextSteps\":\"Send quotation by Friday\"}";
        send(put("/api/meetings/" + meeting + "/report"), execToken, report).andExpect(status().isBadRequest()); // not started yet

        jdbc.update("UPDATE meetings SET starts_at = ?, ends_at = ? WHERE id = ?", LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(2).plusHours(1), meeting);
        send(get("/api/meetings"), execToken, null).andExpect(jsonPath("$.data.length()").value(0)); // agenda: upcoming only
        send(put("/api/meetings/" + meeting + "/report"), execToken, report.replace("positive", "great")).andExpect(status().isBadRequest());
        send(put("/api/meetings/" + meeting + "/report"), execToken, report)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportOutcome").value("positive"))
                .andExpect(jsonPath("$.data.nextSteps").value("Send quotation by Friday"))
                .andExpect(jsonPath("$.data.reportedByName").value(exec.getFullName()));
        send(get("/api/meetings").param("recent", "true"), execToken, null)
                .andExpect(jsonPath("$.data[?(@.id == " + meeting + ")].reportNotes").value(contains("Agreed on scope")));

        // Someone who can't see the customer can't report on it.
        String other = tenant.token(SALES_MANAGER);
        User outsiderExec = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        long team = idOf(send(post("/api/sales-teams"), adminToken, "{\"name\":\"East\",\"managerId\":" + tenant.user(SALES_MANAGER).getId() + "}"));
        send(put("/api/sales-teams/" + team + "/members"), adminToken, "{\"userIds\":[" + outsiderExec.getId() + "]}").andExpect(status().isOk());
        send(put("/api/meetings/" + meeting + "/report"), other, report).andExpect(status().isForbidden());
    }

    @Test
    void assignmentsFollowUpsAndManagerMessagesAreNotifiedAndPushed() throws Exception {
        send(post("/api/devices"), execToken, "{\"token\":\"exec-phone\",\"platform\":\"android\"}").andExpect(status().isOk());
        send(post("/api/devices"), execToken, "{\"token\":\"old-phone\",\"platform\":\"ios\"}").andExpect(status().isOk());
        send(post("/api/devices"), execToken, "{\"token\":\"x\",\"platform\":\"windows\"}").andExpect(status().isBadRequest());
        when(pushSender.send(eq("old-phone"), anyString(), any(), anyMap())).thenReturn(PushSender.Result.INVALID_TOKEN);

        // Assignment by someone else is notified and pushed; the stale token is dropped.
        long lead = idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Ravi\",\"companyName\":\"Acme\",\"industry\":\"trading\",\"source\":\"referral\","
                + "\"ownerId\":" + exec.getId() + ",\"followUpDate\":\"" + LocalDateTime.now().minusHours(1).withNano(0) + "\"}"));
        verify(pushSender).send(eq("exec-phone"), eq("New lead assigned: Ravi"), org.mockito.ArgumentMatchers.contains("assigned by"),
                argThat((Map<String, String> d) -> "lead".equals(d.get("entityType")) && String.valueOf(lead).equals(d.get("entityId"))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_devices WHERE token = 'old-phone'", Long.class)).isZero();

        // Their own records aren't "assignments".
        send(post("/api/leads"), execToken, "{\"leadName\":\"Mine\",\"companyName\":\"Self\",\"industry\":\"trading\",\"source\":\"referral\"}").andExpect(status().isOk());
        long opp = idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customer + ",\"dealValue\":5000,\"ownerId\":" + exec.getId() + "}"));

        // Follow-up due.
        followUpJob.remindDueFollowUps();

        // Manager message to the team.
        String manager = tenant.token(SALES_MANAGER);
        long team = idOf(send(post("/api/sales-teams"), adminToken, "{\"name\":\"West\",\"managerId\":" + tenant.user(SALES_MANAGER).getId() + "}"));
        send(put("/api/sales-teams/" + team + "/members"), adminToken, "{\"userIds\":[" + exec.getId() + "]}").andExpect(status().isOk());
        send(post("/api/notifications/messages"), manager, "{\"title\":\"Team call\",\"body\":\"5pm today\",\"teamId\":" + team + "}")
                .andExpect(jsonPath("$.data.recipients").value(1));
        User outsider = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        send(post("/api/notifications/messages"), manager, "{\"title\":\"Hi\",\"body\":\"x\",\"userIds\":[" + outsider.getId() + "]}").andExpect(status().isForbidden());
        send(post("/api/notifications/messages"), execToken, "{\"title\":\"Hi\",\"body\":\"x\",\"userIds\":[" + outsider.getId() + "]}").andExpect(status().isForbidden());

        String inbox = send(get("/api/notifications"), execToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(4))
                .andExpect(jsonPath("$.data.items[0].type").value("message"))
                .andExpect(jsonPath("$.data.items[0].senderName").value(tenant.user(SALES_MANAGER).getFullName()))
                .andExpect(jsonPath("$.data.items[?(@.type == 'follow_up')].entityId").value(contains((int) lead)))
                .andExpect(jsonPath("$.data.items[?(@.entityType == 'opportunity')].entityId").value(contains((int) opp)))
                .andReturn().getResponse().getContentAsString();
        long first = ((Number) JsonPath.read(inbox, "$.data.items[0].id")).longValue();
        send(patch("/api/notifications/" + first + "/read"), execToken, null).andExpect(status().isOk());
        send(patch("/api/notifications/" + first + "/read"), adminToken, null).andExpect(status().isNotFound()); // not theirs
        send(get("/api/notifications"), execToken, null).andExpect(jsonPath("$.data.unread").value(3));
        send(patch("/api/notifications/read-all"), execToken, null).andExpect(status().isOk());
        send(get("/api/notifications"), execToken, null).andExpect(jsonPath("$.data.unread").value(0));

        // Signing out stops pushes to that phone; signing in as someone else moves it.
        send(post("/api/devices"), adminToken, "{\"token\":\"exec-phone\",\"platform\":\"android\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT user_id FROM user_devices WHERE token = 'exec-phone'", Long.class)).isEqualTo(tenant.user(ADMIN).getId());
        send(post("/api/devices/unregister"), adminToken, "{\"token\":\"exec-phone\",\"platform\":\"android\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_devices WHERE token = 'exec-phone'", Long.class)).isZero();
    }

    @Test
    void aPushThatFailsForATemporaryReasonIsRetriedThenGivenUpOn() throws Exception {
        // ERR-3: Firebase busy on the first try, through on the retry.
        send(post("/api/devices"), execToken, "{\"token\":\"flaky-phone\",\"platform\":\"android\"}").andExpect(status().isOk());
        when(pushSender.send(eq("flaky-phone"), anyString(), any(), anyMap())).thenReturn(PushSender.Result.RETRY_LATER, PushSender.Result.SENT);
        send(post("/api/leads"), adminToken, "{\"leadName\":\"Retry\",\"companyName\":\"Flaky Co\",\"industry\":\"trading\",\"source\":\"referral\","
                + "\"ownerId\":" + exec.getId() + "}").andExpect(status().isOk());
        long retry = jdbc.queryForObject("SELECT r.id FROM push_retries r JOIN user_devices d ON d.id = r.device_id WHERE d.token = 'flaky-phone'", Long.class);
        long notification = jdbc.queryForObject("SELECT notification_id FROM push_retries WHERE id = ?", Long.class, retry);
        assertThat(jdbc.queryForObject("SELECT status FROM notification_deliveries WHERE notification_id = ? AND channel = 'push'", String.class, notification))
                .isEqualTo("queued");

        pushRetryService.retry(retry); // not due yet: nothing happens
        verify(pushSender, times(1)).send(eq("flaky-phone"), anyString(), any(), anyMap());
        jdbc.update("UPDATE push_retries SET next_attempt_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), retry);
        pushRetryService.retry(retry);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_retries WHERE id = ?", Long.class, retry)).isZero();
        assertThat(jdbc.queryForList("SELECT detail FROM notification_deliveries WHERE notification_id = ? AND channel = 'push' AND status = 'sent'", String.class, notification))
                .containsExactly("Delivered on retry 1");

        // Still failing after the last backoff step: given up on and logged as failed.
        when(pushSender.send(eq("flaky-phone"), anyString(), any(), anyMap())).thenReturn(PushSender.Result.RETRY_LATER);
        send(post("/api/leads"), adminToken, "{\"leadName\":\"Retry 2\",\"companyName\":\"Flaky Co\",\"industry\":\"trading\",\"source\":\"referral\","
                + "\"ownerId\":" + exec.getId() + "}").andExpect(status().isOk());
        long second = jdbc.queryForObject("SELECT r.id FROM push_retries r JOIN user_devices d ON d.id = r.device_id WHERE d.token = 'flaky-phone'", Long.class);
        jdbc.update("UPDATE push_retries SET attempts = 4, next_attempt_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), second);
        pushRetryService.retry(second);
        assertThat(jdbc.queryForObject("SELECT status FROM push_retries WHERE id = ?", String.class, second)).isEqualTo("dead");
        assertThat(jdbc.queryForObject("SELECT d.status FROM notification_deliveries d JOIN push_retries r ON r.notification_id = d.notification_id "
                + "WHERE r.id = ? AND d.channel = 'push' ORDER BY d.id DESC LIMIT 1", String.class, second)).isEqualTo("failed");
    }

    @Test
    void opportunitiesCanBeListedByOwner() throws Exception {
        idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customer + ",\"dealValue\":100,\"ownerId\":" + exec.getId() + "}"));
        idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customer + ",\"dealValue\":200}"));
        send(get("/api/opportunities").param("ownerId", String.valueOf(exec.getId())), adminToken, null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].dealValue").value(100));
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
