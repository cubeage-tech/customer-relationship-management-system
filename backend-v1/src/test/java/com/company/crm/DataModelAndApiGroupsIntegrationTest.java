package com.company.crm;

import com.company.crm.communication.integration.TwilioClient;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SRS §9.2 entities DB-E12 (Sales Target), DB-E13 (Attendance Record), DB-E14 (Commission Record),
 * DB-E16 (Document / Attachment) and §10.2 API groups API-G06 (Communications) and API-G09 (Sales Team).
 */
class DataModelAndApiGroupsIntegrationTest extends PostgresIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4 quotation terms".getBytes(StandardCharsets.US_ASCII);

    @Autowired private JdbcTemplate jdbc;

    private TestTenant tenant;
    private String adminToken;

    @BeforeEach
    void setUp() {
        tenant = testTenants.tenantWithAllRoles("Model Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
    }

    @Test
    void salesForceEntitiesExistWithTheirKeyFields() {
        assertThat(columns("sales_targets")).contains("user_id", "team_id", "metric", "target_value", "period_start", "period_end");
        assertThat(columns("attendance_records")).contains("user_id", "check_in_at", "check_out_at", "check_in_lat", "check_out_lat");
        assertThat(columns("commission_entries")).contains("opportunity_id", "user_id", "rule_name", "deal_value", "rate_percent", "amount", "status", "won_at");
        assertThat(columns("customer_documents")).contains("storage_key", "storage_provider", "file_name", "content_type", "size_bytes");
        assertThat(columns("attachments")).contains("entity_type", "entity_id", "storage_key", "storage_provider", "file_name", "content_type", "size_bytes");
    }

    @Test
    void salesTeamApiGroupAnswersAtTheSrsPaths() throws Exception {
        String manager = tenant.token(SALES_MANAGER);
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        long target = idOf(send(post("/api/targets"), manager, "{\"userId\":" + tenant.user(SALES_EXECUTIVE).getId()
                + ",\"metric\":\"revenue\",\"targetValue\":1000,\"periodStart\":\"" + start + "\",\"periodEnd\":\"" + start.plusMonths(1).minusDays(1) + "\"}"));
        send(get("/api/targets"), manager, null).andExpect(jsonPath("$.data[?(@.id == " + target + ")]").exists());
        send(get("/api/sales-targets"), manager, null).andExpect(jsonPath("$.data[?(@.id == " + target + ")]").exists());
        send(post("/api/attendance/check-in"), tenant.token(SALES_EXECUTIVE), null).andExpect(status().isOk());
        send(get("/api/attendance").param("from", LocalDate.now().toString()).param("to", LocalDate.now().toString()), manager, null)
                .andExpect(status().isOk());
        send(get("/api/commissions"), tenant.token(FINANCE_APPROVER), null).andExpect(status().isOk());
        send(delete("/api/targets/" + target), manager, null).andExpect(status().isOk());
    }

    @Test
    void communicationsApiGroupSendsSms() throws Exception {
        long customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Text Co\",\"industry\":\"trading\"}"));
        String body = "{\"customerId\":" + customer + ",\"to\":\"9876501234\",\"body\":\"Your order has shipped\"}";

        when(twilioClient.isSmsEnabled()).thenReturn(false);
        send(post("/api/communications/sms"), adminToken, body).andExpect(status().isServiceUnavailable());

        when(twilioClient.isSmsEnabled()).thenReturn(true);
        when(twilioClient.sendSms(anyString(), anyString())).thenReturn(new TwilioClient.SendResult("SM123", "queued"));
        send(post("/api/communications/sms"), tenant.token(SALES_EXECUTIVE), body).andExpect(status().isForbidden()); // not their customer
        send(post("/api/communications/sms"), adminToken, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("sms"))
                .andExpect(jsonPath("$.data.direction").value("outbound"))
                .andExpect(jsonPath("$.data.counterpart").value("+919876501234"));
        send(post("/api/communications/sms"), tenant.token(FINANCE_APPROVER), body).andExpect(status().isForbidden());
        send(post("/api/communications/sms"), adminToken, "{\"customerId\":" + customer + ",\"to\":\"\",\"body\":\"x\"}").andExpect(status().isBadRequest());
        send(get("/api/communications").param("customerId", String.valueOf(customer)), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.channel == 'sms')].body").value(org.hamcrest.Matchers.hasItem("Your order has shipped")));
    }

    @Test
    void quotationsAndTicketsTakeAttachmentsWithTheirOwnAccessRules() throws Exception {
        String exec = tenant.token(SALES_EXECUTIVE);
        long customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Files Co\",\"industry\":\"trading\",\"ownerId\":"
                + tenant.user(SALES_EXECUTIVE).getId() + "}"));
        long quotation = idOf(send(post("/api/quotations"), exec, "{\"customerId\":" + customer
                + ",\"lineItems\":[{\"productName\":\"Kit\",\"quantity\":1,\"unitPrice\":100,\"discountPercent\":0}]}"));

        long attachment = idOf(mockMvc.perform(multipart("/api/quotations/" + quotation + "/attachments")
                .file(new MockMultipartFile("file", "terms.pdf", "application/pdf", PDF))
                .header("Authorization", "Bearer " + exec)));
        assertThat(jdbc.queryForObject("SELECT storage_provider FROM attachments WHERE id = ?", String.class, attachment)).isEqualTo("local");
        send(get("/api/quotations/" + quotation + "/attachments"), tenant.token(FINANCE_APPROVER), null)
                .andExpect(jsonPath("$.data[0].fileName").value("terms.pdf"))
                .andExpect(jsonPath("$.data[0].uploadedByName").value(tenant.user(SALES_EXECUTIVE).getFullName()));
        byte[] downloaded = send(get("/api/quotations/" + quotation + "/attachments/" + attachment + "/download"), adminToken, null)
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF);

        // Another salesperson can't see this quotation's files; a disguised executable is refused.
        User otherExec = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        send(get("/api/quotations/" + quotation + "/attachments"), testTenants.tokenFor(otherExec), null).andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/quotations/" + quotation + "/attachments")
                        .file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", "MZ\u0090\u0000binary".getBytes(StandardCharsets.ISO_8859_1)))
                        .header("Authorization", "Bearer " + exec))
                .andExpect(status().isBadRequest());
        // Other tenants see nothing.
        TestTenant other = testTenants.tenantWithAllRoles("Files Other " + System.nanoTime());
        send(get("/api/quotations/" + quotation + "/attachments"), other.token(ADMIN), null).andExpect(status().isNotFound());

        send(delete("/api/quotations/" + quotation + "/attachments/" + attachment), exec, null).andExpect(status().isOk());
        send(get("/api/quotations/" + quotation + "/attachments"), exec, null).andExpect(jsonPath("$.data.length()").value(0));

        // Tickets: the assigned agent may attach; another agent may not.
        User agent = tenant.user(SERVICE_AGENT);
        User otherAgent = testTenants.createUser(tenant.tenant(), SERVICE_AGENT);
        long ticket = idOf(send(post("/api/tickets"), adminToken, "{\"customerId\":" + customer + ",\"subject\":\"Broken\",\"priority\":\"high\"}"));
        send(patch("/api/tickets/" + ticket + "/assign"), adminToken, "{\"technicianId\":" + agent.getId() + "}").andExpect(status().isOk());
        idOf(mockMvc.perform(multipart("/api/tickets/" + ticket + "/attachments")
                .file(new MockMultipartFile("file", "photo.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2}))
                .header("Authorization", "Bearer " + tenant.token(SERVICE_AGENT))));
        mockMvc.perform(multipart("/api/tickets/" + ticket + "/attachments")
                        .file(new MockMultipartFile("file", "x.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}))
                        .header("Authorization", "Bearer " + testTenants.tokenFor(otherAgent)))
                .andExpect(status().isForbidden());
        send(get("/api/tickets/" + ticket + "/attachments"), adminToken, null).andExpect(jsonPath("$.data[0].contentType").value("image/png"));
        send(get("/api/tickets/" + ticket + "/attachments"), tenant.token(SALES_EXECUTIVE), null).andExpect(status().isForbidden());
    }

    private List<String> columns(String table) {
        return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = ?", String.class, table);
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
