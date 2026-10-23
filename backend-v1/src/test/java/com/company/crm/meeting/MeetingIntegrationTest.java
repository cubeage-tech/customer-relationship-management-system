package com.company.crm.meeting;

import com.company.crm.meeting.scheduler.MeetingReminderJob;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
