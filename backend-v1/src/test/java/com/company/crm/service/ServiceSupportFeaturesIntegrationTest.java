package com.company.crm.service;

import com.company.crm.common.audit.AuditEventRepository;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.support.scheduler.TicketEscalationJob;
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

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-5.6 meetings on the timeline; FR-6.1 assets + customer support form; FR-6.3b ticket
 * auto-assignment; FR-6.4c SLA escalation; FR-6.5 customer feedback link.
 */
class ServiceSupportFeaturesIntegrationTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TicketEscalationJob escalationJob;
    @Autowired private AuditEventRepository auditEventRepository;

    private TestTenant tenant;
    private String adminToken;
    private User agentA;
    private User agentB;
    private long customerId;
    private String customerEmail;

    @BeforeEach
    void setUp() throws Exception {
        long n = System.nanoTime();
        tenant = testTenants.tenantWithAllRoles("Service Co " + n);
        adminToken = tenant.token(ADMIN);
        agentA = tenant.user(SERVICE_AGENT);
        agentB = testTenants.createUser(tenant.tenant(), SERVICE_AGENT);
        customerEmail = "plant" + n + "@buyer.test";
        customerId = idOf(send(post("/api/customers"), adminToken,
                "{\"companyName\":\"Buyer Ltd\",\"industry\":\"manufacturing\",\"email\":\"" + customerEmail + "\"}"));
    }

    @Test
    void meetingsAppearOnTheCommunicationTimeline() throws Exception {
        send(post("/api/communications/calls"), adminToken, "{\"customerId\":" + customerId + ",\"direction\":\"outbound\",\"outcome\":\"connected\"}")
                .andExpect(status().isOk());
        send(post("/api/meetings"), adminToken, "{\"customerId\":" + customerId + ",\"title\":\"Plant visit\",\"startsAt\":\""
                + LocalDateTime.now().plusDays(3).withNano(0) + "\",\"endsAt\":\"" + LocalDateTime.now().plusDays(3).plusHours(1).withNano(0) + "\"}")
                .andExpect(status().isOk());
        send(get("/api/communications").param("customerId", String.valueOf(customerId)), adminToken, null)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].channel").value("meeting")) // future meeting sorts first (newest)
                .andExpect(jsonPath("$.data[0].subject").value("Plant visit"))
                .andExpect(jsonPath("$.data[0].status").value("scheduled"))
                .andExpect(jsonPath("$.data[1].channel").value("call"));
    }

    @Test
    void assetsAndTheCustomerSupportForm() throws Exception {
        long assetId = idOf(send(post("/api/customers/" + customerId + "/assets"), adminToken,
                "{\"name\":\"Hydraulic press\",\"model\":\"HP-200\",\"serialNumber\":\"SN-77\"}"));
        send(get("/api/customers/" + customerId + "/assets"), tenant.token(SERVICE_AGENT), null).andExpect(jsonPath("$.data.length()").value(1));
        send(post("/api/customers/" + customerId + "/assets"), tenant.token(FINANCE_APPROVER), "{\"name\":\"X\"}").andExpect(status().isForbidden());

        // Staff ticket against the asset; another customer's asset is refused.
        send(post("/api/tickets"), adminToken, ticket("Press leaking", "medium", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetName").value("Hydraulic press (SN-77)"))
                .andExpect(jsonPath("$.data.source").value("staff"));
        long otherCustomer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Other\",\"industry\":\"trading\"}"));
        send(post("/api/tickets"), adminToken, "{\"customerId\":" + otherCustomer + ",\"subject\":\"X\",\"priority\":\"low\",\"assetId\":" + assetId + "}")
                .andExpect(status().isBadRequest());

        // Support form: admin-only link; the public page needs no login.
        send(get("/api/support-form"), tenant.token(SALES_MANAGER), null).andExpect(status().isForbidden());
        String url = JsonPath.read(send(get("/api/support-form"), adminToken, null).andReturn().getResponse().getContentAsString(), "$.data.url");
        String token = url.substring(url.lastIndexOf('/') + 1);
        mockMvc.perform(get("/api/public/support/" + token)).andExpect(jsonPath("$.data.companyName").value(tenant.tenant().getCompanyName()));

        String body = mockMvc.perform(post("/api/public/support/" + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + customerEmail.toUpperCase() + "\",\"subject\":\"Machine stopped\",\"description\":\"Error E4\","
                                + "\"priority\":\"high\",\"serialNumber\":\"sn-77\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long ticketId = ((Number) JsonPath.read(body, "$.data.ticketId")).longValue();
        send(get("/api/tickets/" + ticketId), adminToken, null)
                .andExpect(jsonPath("$.data.customerId").value(customerId))
                .andExpect(jsonPath("$.data.source").value("customer"))
                .andExpect(jsonPath("$.data.reporterEmail").value(customerEmail))
                .andExpect(jsonPath("$.data.assetId").value(assetId))
                .andExpect(jsonPath("$.data.status").value("open"));
        verify(mailService).sendCustomerEmail(eq(customerEmail), contains("ticket #" + ticketId), anyString(), anyString());

        mockMvc.perform(post("/api/public/support/" + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"stranger@nowhere.test\",\"subject\":\"Hi\",\"priority\":\"low\"}"))
                .andExpect(status().isBadRequest());
        send(post("/api/support-form/reset"), adminToken, null).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/support/" + token)).andExpect(status().isNotFound());
    }

    @Test
    void ticketsAreAutoAssignedByRules() throws Exception {
        send(post("/api/ticket-assignment-rules"), adminToken, "{\"name\":\"Critical to B\",\"priority\":10,\"matchPriority\":\"critical\",\"assignUserId\":" + agentB.getId() + "}")
                .andExpect(jsonPath("$.data.assignUserName").isNotEmpty());
        send(post("/api/ticket-assignment-rules"), adminToken, "{\"name\":\"Manufacturing pool\",\"priority\":20,\"matchIndustry\":\"manufacturing\"}")
                .andExpect(jsonPath("$.data.assignUserId").doesNotExist());
        send(post("/api/ticket-assignment-rules"), adminToken, "{\"name\":\"Bad\",\"assignUserId\":" + tenant.user(SALES_EXECUTIVE).getId() + "}")
                .andExpect(status().isBadRequest());
        send(get("/api/ticket-assignment-rules"), tenant.token(SERVICE_AGENT), null).andExpect(status().isForbidden());

        send(post("/api/tickets"), adminToken, ticket("Fire", "critical", null))
                .andExpect(jsonPath("$.data.technicianId").value(agentB.getId()))
                .andExpect(jsonPath("$.data.status").value("assigned"));
        // Round-robin over the active agents (lowest id first).
        long first = Math.min(agentA.getId(), agentB.getId());
        long second = Math.max(agentA.getId(), agentB.getId());
        send(post("/api/tickets"), adminToken, ticket("One", "medium", null)).andExpect(jsonPath("$.data.technicianId").value(first));
        send(post("/api/tickets"), adminToken, ticket("Two", "medium", null)).andExpect(jsonPath("$.data.technicianId").value(second));
        send(post("/api/tickets"), adminToken, ticket("Three", "low", null)).andExpect(jsonPath("$.data.technicianId").value(first));

        // No matching rule: stays in the claim queue.
        long trading = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Shop\",\"industry\":\"trading\"}"));
        send(post("/api/tickets"), adminToken, "{\"customerId\":" + trading + ",\"subject\":\"Queue me\",\"priority\":\"low\"}")
                .andExpect(jsonPath("$.data.technicianId").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("open"));
    }

    @Test
    void atRiskAndBreachedTicketsAreEscalatedOnce() throws Exception {
        long breached = idOf(send(post("/api/tickets"), adminToken, ticket("Late one", "high", null)));
        long atRisk = idOf(send(post("/api/tickets"), adminToken, ticket("Close one", "medium", null)));
        long fine = idOf(send(post("/api/tickets"), adminToken, ticket("Fine one", "low", null)));
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("UPDATE service_tickets SET created_at = ?, sla_due_at = ? WHERE id = ?", now.minusHours(9), now.minusHours(1), breached);
        jdbcTemplate.update("UPDATE service_tickets SET created_at = ?, sla_due_at = ? WHERE id = ?", now.minusHours(22), now.plusHours(2), atRisk);
        send(patch("/api/tickets/" + atRisk + "/assign"), adminToken, "{\"technicianId\":" + agentA.getId() + "}").andExpect(status().isOk());

        escalationJob.escalate();
        escalationJob.escalate();

        String adminEmail = tenant.user(ADMIN).getEmail();
        verify(mailService, times(1)).sendCalendarEmail(eq(adminEmail), contains("SLA breached: ticket #" + breached), anyString(), isNull());
        verify(mailService, times(1)).sendCalendarEmail(eq(adminEmail), contains("SLA at risk: ticket #" + atRisk), anyString(), isNull());
        verify(mailService, times(1)).sendCalendarEmail(eq(agentA.getEmail()), contains("SLA at risk: ticket #" + atRisk), anyString(), isNull());
        verify(mailService, never()).sendCalendarEmail(anyString(), contains("ticket #" + fine), anyString(), any());
        assertThat(auditEventRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(tenant.tenant().getId(), "service_ticket", breached))
                .extracting(e -> e.getAction().name()).contains("TICKET_SLA_ESCALATED");
    }

    @Test
    void customerRatesTheResolvedTicketOnce() throws Exception {
        long id = idOf(send(post("/api/tickets"), adminToken, ticket("Fix it", "medium", null)));
        send(patch("/api/tickets/" + id + "/assign"), adminToken, "{\"technicianId\":" + agentA.getId() + "}").andExpect(status().isOk());
        send(patch("/api/tickets/" + id + "/status"), adminToken, "{\"status\":\"resolved\"}").andExpect(status().isOk());
        send(patch("/api/tickets/" + id + "/status"), adminToken, "{\"status\":\"closed\"}").andExpect(status().isOk());

        // One feedback email (resolve), none for the later close; logged on the timeline.
        verify(mailService, times(1)).sendCustomerEmail(eq(customerEmail), contains("How was our service"), contains("/feedback/"), anyString());
        String token = jdbcTemplate.queryForObject("SELECT feedback_token FROM service_tickets WHERE id = ?", String.class, id);

        mockMvc.perform(get("/api/public/feedback/" + token))
                .andExpect(jsonPath("$.data.subject").value("Fix it"))
                .andExpect(jsonPath("$.data.technicianName").value(agentA.getFullName()))
                .andExpect(jsonPath("$.data.score").doesNotExist());
        mockMvc.perform(post("/api/public/feedback/" + token).contentType(MediaType.APPLICATION_JSON).content("{\"score\":9}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/public/feedback/" + token).contentType(MediaType.APPLICATION_JSON).content("{\"score\":5,\"comment\":\"Quick fix\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(5));
        mockMvc.perform(post("/api/public/feedback/" + token).contentType(MediaType.APPLICATION_JSON).content("{\"score\":1}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/public/feedback/not-a-token")).andExpect(status().isNotFound());

        send(get("/api/tickets/" + id), adminToken, null)
                .andExpect(jsonPath("$.data.feedbackScore").value(5))
                .andExpect(jsonPath("$.data.feedbackComment").value("Quick fix"))
                .andExpect(jsonPath("$.data.technicianId").value(agentA.getId()))
                .andExpect(jsonPath("$.data.feedbackAt").isNotEmpty());
        send(get("/api/communications").param("customerId", String.valueOf(customerId)), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.channel == 'email')].subject").value(hasItem("How was our service? Ticket #" + id)));
    }

    private String ticket(String subject, String priority, Long assetId) {
        return "{\"customerId\":" + customerId + ",\"subject\":\"" + subject + "\",\"priority\":\"" + priority + "\""
                + (assetId != null ? ",\"assetId\":" + assetId : "") + "}";
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
