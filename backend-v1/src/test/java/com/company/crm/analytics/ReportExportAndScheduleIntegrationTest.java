package com.company.crm.analytics;

import com.company.crm.analytics.scheduler.ReportScheduleJob;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REP-SVC service report, REP-CMP campaign report, FR-9.7 PDF/XLSX export, REP-SCH scheduled
 * email delivery, and REP-SCOPE (role and data scope on every one of them).
 */
class ReportExportAndScheduleIntegrationTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReportScheduleJob scheduleJob;

    private TestTenant tenant;
    private String adminToken;
    private User agentA;
    private User agentB;
    private long customer;
    private final LocalDate yesterday = LocalDate.now().minusDays(1);

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Reports Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        agentA = tenant.user(SERVICE_AGENT);
        agentB = testTenants.createUser(tenant.tenant(), SERVICE_AGENT);
        customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Client\",\"industry\":\"trading\"}"));
        clearInvocations(mailService);
    }

    @Test
    void serviceReportCoversVolumeResolutionSlaAndFeedbackWithinScope() throws Exception {
        // Yesterday: A (high, agent A) resolved in 2h within SLA, rated 5; B (low, agent B) resolved in 3h after
        // its SLA, rated 3; C (high, agent B) still open and past its SLA.
        long a = ticket("A", "high", agentA);
        long b = ticket("B", "low", agentB);
        long c = ticket("C", "high", agentB);
        jdbc.update("UPDATE service_tickets SET created_at = ?, sla_due_at = ?, resolved_at = ?, status = 'resolved', feedback_score = 5, feedback_at = ? WHERE id = ?",
                at(9), at(13), at(11), at(12), a);
        jdbc.update("UPDATE service_tickets SET created_at = ?, sla_due_at = ?, resolved_at = ?, status = 'resolved', feedback_score = 3 WHERE id = ?",
                at(9), at(10), at(12), b);
        jdbc.update("UPDATE service_tickets SET created_at = ?, sla_due_at = ? WHERE id = ?", at(9), at(10), c);

        String day = yesterday.toString();
        send(get("/api/analytics/service").param("from", day).param("to", day), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(3))
                .andExpect(jsonPath("$.data.resolved").value(2))
                .andExpect(jsonPath("$.data.avgResolutionHours").value(2.5))
                .andExpect(jsonPath("$.data.resolvedWithinSla").value(1))
                .andExpect(jsonPath("$.data.slaCompliancePercent").value(50.0))
                .andExpect(jsonPath("$.data.openBreached").value(1))
                .andExpect(jsonPath("$.data.feedbackCount").value(2))
                .andExpect(jsonPath("$.data.averageFeedback").value(4.0))
                .andExpect(jsonPath("$.data.byPriority[?(@.key == 'high')].created").value(contains(2)))
                .andExpect(jsonPath("$.data.byTechnician[?(@.key == '" + agentB.getId() + "')].slaCompliancePercent").value(contains(0.0)));
        send(get("/api/analytics/service").param("from", day).param("to", day).param("priority", "low"), adminToken, null)
                .andExpect(jsonPath("$.data.created").value(1));
        send(get("/api/analytics/service").param("priority", "urgent"), adminToken, null).andExpect(status().isBadRequest());

        // REP-SCOPE: an agent sees only their own tickets, and can't ask for someone else's.
        String agent = tenant.token(SERVICE_AGENT);
        send(get("/api/analytics/service").param("from", day).param("to", day), agent, null)
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.averageFeedback").value(5.0))
                .andExpect(jsonPath("$.data.openBreached").value(0));
        send(get("/api/analytics/service").param("technicianId", String.valueOf(agentB.getId())), agent, null).andExpect(status().isForbidden());
        send(get("/api/analytics/service"), tenant.token(SALES_MANAGER), null).andExpect(status().isForbidden());
    }

    @Test
    void campaignReportShowsSentOpenedClickedConvertedPerCampaign() throws Exception {
        String marketing = tenant.token(MARKETING_EXECUTIVE);
        long expo = idOf(send(post("/api/campaigns"), marketing, "{\"name\":\"Expo\",\"channel\":\"event\",\"startDate\":\"" + LocalDate.now()
                + "\",\"actualCost\":1000}"));
        idOf(send(post("/api/campaigns"), marketing, "{\"name\":\"Last year\",\"channel\":\"event\",\"startDate\":\"" + LocalDate.now().minusYears(1)
                + "\",\"endDate\":\"" + LocalDate.now().minusYears(1).plusDays(5) + "\"}"));
        long second = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Second\",\"industry\":\"trading\"}"));
        jdbc.update("INSERT INTO campaign_recipients (tenant_id, campaign_id, customer_id, address, status, tracking_token, sent_at, opened_at, clicked_at)"
                + " VALUES (?, ?, ?, 'a@x.test', 'sent', ?, now(), now(), now()), (?, ?, ?, 'b@x.test', 'sent', ?, now(), NULL, NULL)",
                tenant.tenant().getId(), expo, customer, "t" + System.nanoTime(), tenant.tenant().getId(), expo, second, "u" + System.nanoTime());
        send(post("/api/leads"), adminToken, "{\"leadName\":\"Visitor\",\"companyName\":\"Stall\",\"industry\":\"trading\",\"source\":\"exhibition\",\"campaignId\":" + expo + "}")
                .andExpect(status().isOk());

        String today = LocalDate.now().toString();
        send(get("/api/analytics/campaigns").param("from", today).param("to", today), marketing, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.campaigns[*].name").value(contains("Expo")))
                .andExpect(jsonPath("$.data.campaigns[0].sent").value(2))
                .andExpect(jsonPath("$.data.campaigns[0].opened").value(1))
                .andExpect(jsonPath("$.data.campaigns[0].clicked").value(1))
                .andExpect(jsonPath("$.data.campaigns[0].converted").value(0))
                .andExpect(jsonPath("$.data.campaigns[0].openRate").value(50.0))
                .andExpect(jsonPath("$.data.campaigns[0].leadsGenerated").value(1))
                .andExpect(jsonPath("$.data.campaigns[0].costPerLead").value(1000.0))
                .andExpect(jsonPath("$.data.sent").value(2));
        send(get("/api/analytics/campaigns"), tenant.token(SALES_EXECUTIVE), null).andExpect(status().isForbidden());
        send(get("/api/analytics/campaigns"), tenant.token(FINANCE_APPROVER), null).andExpect(status().isForbidden());
    }

    @Test
    void reportsExportAsPdfAndExcelWithTheSameScope() throws Exception {
        long deal = idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customer + ",\"dealValue\":2500,\"productService\":\"Pump & Motor\"}"));
        send(patch("/api/opportunities/" + deal + "/stage"), adminToken, "{\"stage\":\"won\",\"force\":true}").andExpect(status().isOk());

        MockHttpServletResponse pdf = send(get("/api/analytics/export/revenue").param("format", "pdf"), tenant.token(FINANCE_APPROVER), null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("revenue-report-")))
                .andReturn().getResponse();
        assertThat(new String(pdf.getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        MockHttpServletResponse xlsx = send(get("/api/analytics/export/revenue").param("format", "xlsx"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".xlsx")))
                .andReturn().getResponse();
        Map<String, String> parts = unzip(xlsx.getContentAsByteArray());
        assertThat(parts).containsKeys("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml");
        assertThat(parts.get("xl/workbook.xml")).contains("name=\"Summary\"", "name=\"By product\"");
        assertThat(parts.get("xl/worksheets/sheet1.xml")).contains("Revenue report");
        assertThat(String.join("", parts.values())).contains("Pump &amp; Motor").contains("<v>2500.00</v>");

        // REP-SCOPE: the export follows the report's role rules and the caller's data scope.
        send(get("/api/analytics/export/revenue"), tenant.token(SALES_MANAGER), null).andExpect(status().isForbidden());
        send(get("/api/analytics/export/service").param("format", "xlsx"), tenant.token(SERVICE_AGENT), null).andExpect(status().isOk());
        User outsider = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        long team = idOf(send(post("/api/sales-teams"), adminToken, "{\"name\":\"North\",\"managerId\":" + tenant.user(SALES_MANAGER).getId() + "}"));
        send(put("/api/sales-teams/" + team + "/members"), adminToken, "{\"userIds\":[" + tenant.user(SALES_EXECUTIVE).getId() + "]}").andExpect(status().isOk());
        send(get("/api/analytics/export/sales").param("userId", String.valueOf(outsider.getId())), tenant.token(SALES_MANAGER), null)
                .andExpect(status().isForbidden());
        send(get("/api/analytics/export/sales").param("format", "docx"), adminToken, null).andExpect(status().isBadRequest());
        send(get("/api/analytics/export/payroll"), adminToken, null).andExpect(status().isBadRequest());
    }

    @Test
    void scheduledReportsAreEmailedOnceAsTheOwner() throws Exception {
        String finance = tenant.token(FINANCE_APPROVER);
        String financeEmail = tenant.user(FINANCE_APPROVER).getEmail().toLowerCase();
        String adminEmail = tenant.user(ADMIN).getEmail().toLowerCase();
        String body = "{\"name\":\"Weekly revenue\",\"reportType\":\"revenue\",\"format\":\"xlsx\",\"frequency\":\"weekly\",\"recipients\":[\""
                + financeEmail + "\",\"" + adminEmail.toUpperCase() + "\"]}";
        String created = send(post("/api/report-schedules"), finance, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipients").value(contains(financeEmail, adminEmail)))
                .andExpect(jsonPath("$.data.hourOfDay").value(7))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        LocalDateTime next = LocalDateTime.parse(JsonPath.read(created, "$.data.nextRunAt"));
        assertThat(next.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(next.getHour()).isEqualTo(7);

        // Recipients stay inside the company; a role can only schedule reports it may view, within its scope.
        send(post("/api/report-schedules"), finance, body.replace(financeEmail, "someone@outside.test")).andExpect(status().isBadRequest());
        send(post("/api/report-schedules"), tenant.token(SALES_MANAGER), body).andExpect(status().isForbidden());
        send(post("/api/report-schedules"), finance, body.replace("\"frequency\":\"weekly\"", "\"frequency\":\"hourly\"")).andExpect(status().isBadRequest());

        // Only the owner (or an admin) sees and changes it.
        send(get("/api/report-schedules"), tenant.token(MARKETING_EXECUTIVE), null)
                .andExpect(jsonPath("$.data[*].id").value(not(hasItem((int) id))));
        send(get("/api/report-schedules"), adminToken, null).andExpect(jsonPath("$.data[*].id").value(hasItem((int) id)));
        send(put("/api/report-schedules/" + id), tenant.token(MARKETING_EXECUTIVE), body).andExpect(status().isForbidden());

        // The job sends a due schedule once, as an XLSX attachment, and moves it to the next Monday.
        jdbc.update("UPDATE report_schedules SET next_run_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), id);
        scheduleJob.sendDueReports();
        scheduleJob.sendDueReports();
        ArgumentCaptor<String[]> to = ArgumentCaptor.forClass(String[].class);
        ArgumentCaptor<String> filename = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(mailService, times(1)).sendReportEmail(to.capture(), anyString(), anyString(), filename.capture(), bytes.capture(),
                eq("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(to.getValue()).containsExactly(financeEmail, adminEmail);
        assertThat(filename.getValue()).startsWith("revenue-report-").endsWith(".xlsx");
        assertThat(new String(bytes.getValue(), 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        send(get("/api/report-schedules"), finance, null)
                .andExpect(jsonPath("$.data[0].lastStatus").value("sent"))
                .andExpect(jsonPath("$.data[0].nextRunAt").value(containsString("T07:00")));

        // REP-SCOPE: a schedule runs with its owner's access at run time — a deactivated owner gets nothing sent.
        jdbc.update("UPDATE users SET status = 'deactivated' WHERE id = ?", tenant.user(FINANCE_APPROVER).getId());
        send(post("/api/report-schedules/" + id + "/send"), adminToken, null)
                .andExpect(jsonPath("$.data.lastStatus").value("failed"))
                .andExpect(jsonPath("$.data.lastError").value(containsString("not active")));
        verify(mailService, times(1)).sendReportEmail(any(), anyString(), anyString(), anyString(), any(), anyString());

        send(delete("/api/report-schedules/" + id), adminToken, null).andExpect(status().isOk());
        send(get("/api/report-schedules"), adminToken, null).andExpect(jsonPath("$.data.length()").value(0));
    }

    private LocalDateTime at(int hour) {
        return yesterday.atTime(hour, 0);
    }

    private long ticket(String subject, String priority, User technician) throws Exception {
        long id = idOf(send(post("/api/tickets"), adminToken, "{\"customerId\":" + customer + ",\"subject\":\"" + subject
                + "\",\"priority\":\"" + priority + "\"}"));
        send(patch("/api/tickets/" + id + "/assign"), adminToken, "{\"technicianId\":" + technician.getId() + "}").andExpect(status().isOk());
        return id;
    }

    private static Map<String, String> unzip(byte[] zip) throws Exception {
        Map<String, String> parts = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                parts.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return parts;
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
