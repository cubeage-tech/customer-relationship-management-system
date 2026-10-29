package com.company.crm.meeting;

import com.company.crm.ai.llm.LlmGateway;
import com.company.crm.ai.service.MeetingSummaryService.ActionPlan;
import com.company.crm.ai.service.MeetingSummaryService.PlannedAction;
import com.company.crm.ai.service.MeetingSummaryService.SummaryPlan;
import com.company.crm.task.scheduler.TaskReminderJob;
import com.company.crm.meeting.scheduler.MeetingReminderJob;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** FR-5.5a scheduling, FR-5.5b reminders, FR-5.5c iCalendar invites, downloads and feeds. */
class MeetingIntegrationTest extends PostgresIntegrationTest {

    @Autowired private MeetingReminderJob reminderJob;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private LlmGateway llm;
    @Autowired private TaskReminderJob taskReminders;

    private TestTenant tenant;
    private String adminToken;
    private User admin;
    private long customerId;
    private String customerEmail;

    @BeforeEach
    void setUp() throws Exception {
        long n = System.nanoTime();
        tenant = testTenants.tenantWithAllRoles("Meetings Co " + n);
        adminToken = tenant.token(ADMIN);
        admin = tenant.user(ADMIN);
        customerEmail = "buyer" + n + "@abc.test";
        customerId = idOf(send(post("/api/customers"), adminToken,
                "{\"companyName\":\"ABC Engineering\",\"industry\":\"trading\",\"email\":\"" + customerEmail + "\"}"));
    }

    @Test
    void scheduleRescheduleCancelWithCalendarInvites() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MINUTES);
        long id = idOf(schedule(meeting("customerId", customerId, "Site visit", start, 60, 15, true))
                .andExpect(jsonPath("$.data.status").value("scheduled"))
                .andExpect(jsonPath("$.data.organizerName").isNotEmpty()));

        // Invite to the organizer and (included) the customer, each with an .ics; the customer's is on the timeline.
        verify(mailService).sendCalendarEmail(eq(admin.getEmail()), contains("Invitation: Site visit"), anyString(), contains("METHOD:REQUEST"));
        verify(mailService).sendCustomerEmail(eq(customerEmail), contains("Invitation"), anyString(), anyString(), contains("ATTENDEE"));
        send(get("/api/communications").param("customerId", String.valueOf(customerId)), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.channel == 'email')].status").value(hasItem("sent")));

        send(get("/api/meetings").param("customerId", String.valueOf(customerId)), tenant.token(FINANCE_APPROVER), null)
                .andExpect(jsonPath("$.data.length()").value(1));
        send(get("/api/meetings"), adminToken, null).andExpect(jsonPath("$.data[0].id").value(id)); // my agenda

        String ics = send(get("/api/meetings/" + id + "/ics"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andReturn().getResponse().getContentAsString();
        assertThat(ics).contains("BEGIN:VEVENT", "SUMMARY:Site visit", "TRIGGER:-PT15M", "STATUS:CONFIRMED", "\r\n")
                .containsPattern("DTSTART:\\d{8}T\\d{6}Z");

        send(put("/api/meetings/" + id), adminToken, meeting("customerId", customerId, "Site visit", start.plusHours(3), 60, 15, true))
                .andExpect(status().isOk());
        verify(mailService).sendCalendarEmail(eq(admin.getEmail()), contains("Updated: Site visit"), anyString(), contains("SEQUENCE:1"));

        send(patch("/api/meetings/" + id + "/cancel"), adminToken, null).andExpect(jsonPath("$.data.status").value("cancelled"));
        verify(mailService).sendCalendarEmail(eq(admin.getEmail()), contains("Cancelled"), anyString(), contains("METHOD:CANCEL"));
        send(put("/api/meetings/" + id), adminToken, meeting("customerId", customerId, "Again", start, 30, null, false))
                .andExpect(status().isBadRequest());
        send(get("/api/meetings"), adminToken, null).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void remindersGoOutOnceWhenDue() throws Exception {
        // Starts in 10 minutes with a 15-minute reminder → due now. Another one is not yet due.
        LocalDateTime soon = LocalDateTime.now().plusMinutes(10);
        long due = idOf(schedule(meeting("customerId", customerId, "Quick call", soon, 30, 15, true)));
        schedule(meeting("customerId", customerId, "Next week", LocalDateTime.now().plusDays(7), 30, 60, true));
        schedule(meeting("customerId", customerId, "No reminder", soon, 30, null, false));

        reminderJob.sendDueReminders();
        reminderJob.sendDueReminders();

        verify(mailService, times(1)).sendCalendarEmail(eq(admin.getEmail()), contains("Reminder: Quick call"), anyString(), isNull());
        verify(mailService, never()).sendCalendarEmail(anyString(), contains("Reminder: Next week"), anyString(), any());
        verify(mailService, never()).sendCalendarEmail(anyString(), contains("Reminder: No reminder"), anyString(), any());
        verify(mailService, times(1)).sendCustomerEmail(eq(customerEmail), contains("Reminder: Quick call"), anyString(), anyString());
        send(get("/api/meetings").param("customerId", String.valueOf(customerId)), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.id == " + due + ")].reminderSentAt").isNotEmpty());
    }

    @Test
    void personalCalendarFeedIsPrivateAndResettable() throws Exception {
        long id = idOf(schedule(meeting("customerId", customerId, "Demo", LocalDateTime.now().plusDays(1), 45, null, false)));
        String url = JsonPath.read(send(get("/api/meetings/calendar-feed"), adminToken, null)
                .andReturn().getResponse().getContentAsString(), "$.data.url");
        assertThat(url).contains("/api/public/calendar/").endsWith(".ics");
        String path = URI.create(url).getPath();

        String feed = mockMvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andReturn().getResponse().getContentAsString();
        assertThat(feed).contains("SUMMARY:Demo", "METHOD:PUBLISH");
        // Not included → no attendee and no customer email.
        assertThat(feed).doesNotContain("ATTENDEE");
        verify(mailService, never()).sendCustomerEmail(eq(customerEmail), anyString(), anyString(), anyString(), anyString());

        send(patch("/api/meetings/" + id + "/cancel"), adminToken, null).andExpect(status().isOk());
        assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString()).contains("STATUS:CANCELLED");

        send(post("/api/meetings/calendar-feed/reset"), adminToken, null).andExpect(status().isOk());
        mockMvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @Test
    void validationAndAccess() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        schedule(meeting("customerId", customerId, "Backwards", start, -30, null, false)).andExpect(status().isBadRequest());
        schedule(meeting("customerId", customerId, "Yesterday", LocalDateTime.now().minusDays(1), 30, null, false)).andExpect(status().isBadRequest());
        send(post("/api/meetings"), adminToken, "{\"title\":\"Nobody\",\"startsAt\":\"" + start + "\",\"endsAt\":\"" + start.plusHours(1) + "\"}")
                .andExpect(status().isBadRequest());
        send(post("/api/meetings"), tenant.token(FINANCE_APPROVER), meeting("customerId", customerId, "X", start, 30, null, false))
                .andExpect(status().isForbidden());

        // A sales executive can't schedule on a customer they don't own; another tenant can't see it at all.
        send(post("/api/meetings"), tenant.token(SALES_EXECUTIVE), meeting("customerId", customerId, "X", start, 30, null, false))
                .andExpect(status().isForbidden());
        TestTenant other = testTenants.tenantWithAllRoles("Other Meetings " + System.nanoTime());
        send(get("/api/meetings").param("customerId", String.valueOf(customerId)), other.token(ADMIN), null).andExpect(status().isNotFound());

        // Meetings with a lead, inviting the lead contact.
        long leadId = idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Ravi\",\"companyName\":\"Lead Co\","
                + "\"industry\":\"trading\",\"source\":\"referral\",\"contactEmail\":\"ravi@lead.test\"}"));
        schedule(meeting("leadId", leadId, "Intro call", start, 30, null, true)).andExpect(status().isOk());
        verify(mailService).sendCustomerEmail(eq("ravi@lead.test"), contains("Invitation: Intro call"), anyString(), anyString(), anyString());
        send(get("/api/meetings").param("leadId", String.valueOf(leadId)), adminToken, null).andExpect(jsonPath("$.data[0].leadName").value("Ravi"));
    }

    private static String meeting(String targetField, long targetId, String title, LocalDateTime start, int minutes,
                                  Integer reminder, boolean includeCustomer) {
        return "{\"" + targetField + "\":" + targetId + ",\"title\":\"" + title + "\",\"startsAt\":\"" + start
                + "\",\"endsAt\":\"" + start.plusMinutes(minutes) + "\",\"location\":\"Pune office\""
                + (reminder != null ? ",\"reminderMinutes\":" + reminder : "") + ",\"includeCustomer\":" + includeCustomer + "}";
    }

    // ==================== FR-AI-7.1 ====================

    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x42, (byte) 0x86, (byte) 0x81, 0x01, 0x42, (byte) 0xF7, (byte) 0x81, 0x01};

    @Test
    void meetingsAreRecordedOnlyWithConsentAndEveryPlayIsAudited() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusMinutes(5).truncatedTo(ChronoUnit.MINUTES);
        long id = idOf(schedule(meeting("customerId", customerId, "Demo call", start, 30, null, false)));

        // No consent recorded: refused, nothing stored.
        upload(id, WEBM, false, adminToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RECORDING_CONSENT_REQUIRED"));
        upload(id, "%PDF-1.4 not audio".getBytes(), true, adminToken).andExpect(status().isBadRequest());
        upload(id, WEBM, true, tenant.token(FINANCE_APPROVER)).andExpect(status().isForbidden());
        long later = idOf(schedule(meeting("customerId", customerId, "Next month", start.plusDays(30), 30, null, false)));
        upload(later, WEBM, true, adminToken).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meeting_recordings", Long.class)).isZero();

        long recording = idOf(upload(id, WEBM, true, adminToken)
                .andExpect(jsonPath("$.data.contentType").value("audio/webm"))
                .andExpect(jsonPath("$.data.consentGivenBy").value("Ravi Kumar (ABC Engineering)"))
                .andExpect(jsonPath("$.data.consentMethod").value("verbal"))
                .andExpect(jsonPath("$.data.durationSeconds").value(95)));
        send(get("/api/meetings/" + id + "/recordings"), adminToken, null)
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].consentAt").isString());
        byte[] played = send(get("/api/meetings/" + id + "/recordings/" + recording + "/audio"), adminToken, null)
                .andExpect(status().isOk()).andExpect(content().contentType("audio/webm"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(played).isEqualTo(WEBM);
        assertThat(jdbc.queryForObject("SELECT details FROM audit_events WHERE action = 'MEETING_RECORDING_ADDED' AND entity_id = ?", String.class, id))
                .isEqualTo("recording #" + recording + ", consent verbal by Ravi Kumar (ABC Engineering)");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'MEETING_RECORDING_PLAYED' AND entity_id = ?", Long.class, id)).isOne();

        // Consent withdrawn: deleted, with the reason audited.
        send(delete("/api/meetings/" + id + "/recordings/" + recording).param("reason", "consent withdrawn"), adminToken, null).andExpect(status().isOk());
        send(get("/api/meetings/" + id + "/recordings"), adminToken, null).andExpect(jsonPath("$.data.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT details FROM audit_events WHERE action = 'MEETING_RECORDING_DELETED' AND entity_id = ?", String.class, id))
                .isEqualTo("recording #" + recording + ": consent withdrawn");
        send(get("/api/meetings/" + id + "/recordings/" + recording + "/audio"), adminToken, null).andExpect(status().isNotFound());

        // Erasing the customer's data removes their recordings.
        idOf(upload(id, WEBM, true, adminToken));
        long erase = idOf(send(post("/api/data-requests"), adminToken, "{\"subjectType\":\"customer\",\"subjectId\":" + customerId + ",\"requestType\":\"erasure\"}"));
        send(post("/api/data-requests/" + erase + "/erase"), adminToken, "{\"confirm\":\"ERASE\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meeting_recordings WHERE meeting_id = ?", Long.class, id)).isZero();
    }

    // ==================== FR-AI-7.2 ====================

    @Test
    void theConversationIsKeptWithConsentAndSummarisedByTheAi() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusMinutes(5).truncatedTo(ChronoUnit.MINUTES);
        long id = idOf(schedule(meeting("customerId", customerId, "Pump review", start, 30, null, false)));
        // JSON-escaped newlines: a VTT file as Zoom/Teams export it
        String vtt = "WEBVTT\\n\\n1\\n00:00:01.000 --> 00:00:04.000\\nRavi: Call me on +91 98765 43210, the pumps are too loud.\\n\\n"
                + "2\\n00:00:05.000 --> 00:00:09.000\\nAsha: We will send a quote for the quieter model by Friday.";
        String pasted = "{\"text\":\"" + vtt + "\",\"source\":\"pasted\"";

        send(put("/api/meetings/" + id + "/transcript"), adminToken, pasted + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("RECORDING_CONSENT_REQUIRED"));
        String consent = pasted + ",\"consentConfirmed\":true,\"consentGivenBy\":\"Ravi Kumar\",\"consentMethod\":\"verbal\"}";
        send(put("/api/meetings/" + id + "/transcript"), adminToken, consent)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transcript").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("pumps are too loud"), org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("-->")))))
                .andExpect(jsonPath("$.data.consentGivenBy").value("Ravi Kumar"));
        assertThat(jdbc.queryForObject("SELECT transcript FROM meetings WHERE id = ?", String.class, id)).doesNotContain("pumps are too loud");

        when(llm.isConfigured()).thenReturn(true);
        when(llm.structured(anyString(), anyString(), eq(SummaryPlan.class))).thenReturn(java.util.Optional.of(new SummaryPlan(
                "Ravi finds the pumps too loud; we will quote a quieter model.", java.util.List.of("Noise of the current pumps"), java.util.List.of(),
                java.util.List.of("Pumps are too loud"), "negative", java.util.List.of("Asha: send a quote for the quieter model by Friday"))));
        send(post("/api/meetings/" + id + "/ai-summary"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerSentiment").value("negative"))
                .andExpect(jsonPath("$.data.nextSteps[0]").value(org.hamcrest.Matchers.containsString("by Friday")));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), prompt.capture(), eq(SummaryPlan.class));
        assertThat(prompt.getValue()).contains("Customer: ABC Engineering", "Meeting: Pump review", "pumps are too loud").doesNotContain("98765");
        send(get("/api/meetings/" + id + "/conversation"), tenant.token(FINANCE_APPROVER), null)
                .andExpect(jsonPath("$.data.summary.overview").value(org.hamcrest.Matchers.containsString("quieter model")))
                .andExpect(jsonPath("$.data.summaryOutdated").value(false));

        // A newer transcript marks the summary as outdated.
        Thread.sleep(20);
        send(put("/api/meetings/" + id + "/transcript"), adminToken, consent).andExpect(jsonPath("$.data.summaryOutdated").value(true));

        // Message text withheld from the AI: no summary.
        send(put("/api/ai/settings"), adminToken, "{\"aiEnabled\":true,\"messageContent\":false}").andExpect(status().isOk());
        send(post("/api/meetings/" + id + "/ai-summary"), adminToken, null).andExpect(status().is4xxClientError());
        send(put("/api/ai/settings"), adminToken, "{\"aiEnabled\":true,\"messageContent\":true}").andExpect(status().isOk());

        // Captured with a recording: its consent covers it — and withdrawing it removes the conversation.
        long recording = idOf(upload(id, WEBM, true, adminToken));
        send(put("/api/meetings/" + id + "/transcript"), adminToken, "{\"text\":\"Ravi: the new pumps sound fine.\",\"source\":\"browser\",\"recordingId\":" + recording + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.consentGivenBy").value("Ravi Kumar (ABC Engineering)"));
        send(post("/api/meetings/" + id + "/ai-summary"), adminToken, null).andExpect(status().isOk());
        send(delete("/api/meetings/" + id + "/recordings/" + recording).param("reason", "consent withdrawn"), adminToken, null).andExpect(status().isOk());
        send(get("/api/meetings/" + id + "/conversation"), adminToken, null)
                .andExpect(jsonPath("$.data.transcript").doesNotExist()).andExpect(jsonPath("$.data.summary").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'MEETING_SUMMARY_GENERATED' AND entity_id = ?", Long.class, id)).isEqualTo(2);
    }

    // ==================== FR-AI-7.3 ====================

    @Test
    void actionItemsBecomeFollowUpTasksOnceConfirmed() throws Exception {
        User exec = tenant.user(SALES_EXECUTIVE);
        LocalDateTime start = LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.MINUTES);
        long id = idOf(schedule(meeting("customerId", customerId, "Pump review", start.plusHours(2), 30, null, false)));
        jdbc.update("UPDATE meetings SET starts_at = ?, ends_at = ? WHERE id = ?", start, start.plusMinutes(30), id);
        send(post("/api/meetings/" + id + "/action-items"), adminToken, null).andExpect(status().isBadRequest()); // no transcript yet
        send(put("/api/meetings/" + id + "/transcript"), adminToken, "{\"text\":\"Ravi: send me the quote. " + exec.getFullName()
                + ": I will send it on " + start.toLocalDate().plusDays(4) + ". Ravi: I will share the site drawings.\",\"source\":\"pasted\","
                + "\"consentConfirmed\":true,\"consentGivenBy\":\"Ravi Kumar\",\"consentMethod\":\"verbal\"}").andExpect(status().isOk());

        when(llm.isConfigured()).thenReturn(true);
        when(llm.structured(anyString(), anyString(), eq(ActionPlan.class))).thenReturn(java.util.Optional.of(new ActionPlan(java.util.List.of(
                new PlannedAction("Send the quote for the quieter pump", "Model QP-200", exec.getFullName(), false, start.toLocalDate().plusDays(4).toString()),
                new PlannedAction("Share the site drawings", "", "Ravi", true, "")))));
        String proposed = send(post("/api/meetings/" + id + "/action-items"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assigneeId").value(exec.getId().intValue()))
                .andExpect(jsonPath("$.data[0].dueDate").value(start.toLocalDate().plusDays(4).toString()))
                .andExpect(jsonPath("$.data[1].title").value("Follow up: Share the site drawings"))
                .andExpect(jsonPath("$.data[1].assigneeId").value(admin.getId().intValue())) // the organizer
                .andExpect(jsonPath("$.data[1].dueDate").value(start.toLocalDate().plusDays(3).toString()))
                .andReturn().getResponse().getContentAsString();
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), prompt.capture(), eq(ActionPlan.class));
        assertThat(prompt.getValue()).contains("Our people:", exec.getFullName(), "send me the quote");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM follow_up_tasks WHERE meeting_id = ?", Long.class, id)).isZero(); // nothing yet

        // The user keeps both (editing the first) and creates them.
        String items = "{\"items\":[{\"title\":\"Send the QP-200 quote\",\"notes\":\"Model QP-200\",\"assigneeId\":" + exec.getId()
                + ",\"dueDate\":\"" + start.toLocalDate().plusDays(4) + "\"},{\"title\":\"Follow up: Share the site drawings\",\"assigneeId\":"
                + admin.getId() + ",\"dueDate\":\"" + LocalDateTime.now().toLocalDate() + "\"}]}";
        send(post("/api/meetings/" + id + "/tasks"), adminToken, items)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].customerId").value((int) customerId))
                .andExpect(jsonPath("$.data[0].source").value("meeting_ai"))
                .andExpect(jsonPath("$.data[0].meetingTitle").value("Pump review"));
        assertThat(jdbc.queryForObject("SELECT details FROM audit_events WHERE action = 'FOLLOW_UP_TASKS_CREATED' AND entity_id = ?", String.class, id))
                .isEqualTo("2 task(s) from the meeting's action items");

        // Each sees their own; the record shows all it may.
        String execToken = tenant.token(SALES_EXECUTIVE);
        String mine = send(get("/api/tasks"), execToken, null).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Send the QP-200 quote")).andReturn().getResponse().getContentAsString();
        long execTask = ((Number) JsonPath.read(mine, "$.data[0].id")).longValue();
        send(get("/api/tasks").param("customerId", String.valueOf(customerId)), adminToken, null).andExpect(jsonPath("$.data.length()").value(2));

        // Done by the assignee.
        send(patch("/api/tasks/" + execTask), execToken, "{\"status\":\"done\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completedAt").isString());

        // Due today: the assignee is reminded once.
        taskReminders.remindDueTasks();
        taskReminders.remindDueTasks();
        send(get("/api/notifications"), adminToken, null)
                .andExpect(jsonPath("$.data.items[?(@.title == 'Task due: Follow up: Share the site drawings')]").value(org.hamcrest.Matchers.hasSize(1)));

        // By hand: linked to one record, given to a colleague of this tenant.
        send(post("/api/tasks"), adminToken, "{\"title\":\"Call back\"}").andExpect(status().isBadRequest());
        User outsider = testTenants.tenantWithAllRoles("Other " + System.nanoTime()).user(ADMIN);
        send(post("/api/tasks"), adminToken, "{\"title\":\"Call back\",\"customerId\":" + customerId + ",\"assigneeId\":" + outsider.getId() + "}")
                .andExpect(status().isBadRequest());
        send(post("/api/tasks"), adminToken, "{\"title\":\"Call back\",\"customerId\":" + customerId + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.source").value("manual"))
                .andExpect(jsonPath("$.data.assigneeId").value(admin.getId().intValue()));
        send(post("/api/tasks"), tenant.token(FINANCE_APPROVER), "{\"title\":\"x\",\"customerId\":" + customerId + "}").andExpect(status().isForbidden());
    }

    private ResultActions upload(long meetingId, byte[] audio, boolean consent, String token) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/meetings/" + meetingId + "/recordings")
                .file(new MockMultipartFile("file", "call.webm", "audio/webm", audio));
        if (consent) {
            request.param("consentConfirmed", "true").param("consentGivenBy", "Ravi Kumar (ABC Engineering)")
                    .param("consentMethod", "verbal").param("durationSeconds", "95");
        }
        request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request);
    }

    private ResultActions schedule(String body) throws Exception {
        return send(post("/api/meetings"), adminToken, body);
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
