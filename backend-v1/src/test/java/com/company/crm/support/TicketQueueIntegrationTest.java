package com.company.crm.support;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditEventRepository;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.support.repository.ServiceTicketRepository;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Decision 4: agents work their own tickets plus the unassigned queue, and claiming is race-safe. */
class TicketQueueIntegrationTest extends PostgresIntegrationTest {

    @Autowired private ServiceTicketRepository ticketRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    private TestTenant tenant;
    private User agent;
    private User otherAgent;
    private String adminToken;
    private String agentToken;
    private String otherAgentToken;
    private long customerId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Queue Co " + System.nanoTime());
        agent = tenant.user(SERVICE_AGENT);
        otherAgent = testTenants.createUser(tenant.tenant(), SERVICE_AGENT);
        adminToken = tenant.token(ADMIN);
        agentToken = tenant.token(SERVICE_AGENT);
        otherAgentToken = testTenants.tokenFor(otherAgent);
        customerId = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Queue Customer\",\"industry\":\"trading\"}"));
    }

    @Test
    void agentSeesOwnPlusQueue_scopesNarrowIt_adminUnchanged() throws Exception {
        long queued = createTicket("Queued");
        long mine = createTicket("Mine");
        long theirs = createTicket("Theirs");
        assign(mine, agent);
        assign(theirs, otherAgent);

        expectSubjects(get("/api/tickets"), agentToken, "Queued", "Mine");
        expectSubjects(get("/api/tickets").param("scope", "mine"), agentToken, "Mine");
        expectSubjects(get("/api/tickets").param("scope", "queue"), agentToken, "Queued");
        expectSubjects(get("/api/tickets"), adminToken, "Queued", "Mine", "Theirs"); // admin: whole tenant, as before

        // View vs edit: a queue ticket can be opened but not edited until claimed; others' tickets stay hidden.
        send(get("/api/tickets/" + queued), agentToken, null).andExpect(status().isOk());
        send(put("/api/tickets/" + queued), agentToken,
                "{\"customerId\":" + customerId + ",\"subject\":\"Edited\",\"priority\":\"high\"}").andExpect(status().isForbidden());
        send(get("/api/tickets/" + theirs), agentToken, null).andExpect(status().isForbidden());

        // Opt-in paging, server-side.
        send(get("/api/tickets").param("page", "0").param("size", "1"), agentToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        send(get("/api/tickets").param("page", "0").param("sort", "password"), agentToken, null).andExpect(status().isBadRequest());
        send(get("/api/tickets").param("scope", "everything"), agentToken, null).andExpect(status().isBadRequest());
    }

    @Test
    void claimAssignsToMe_isIdempotent_andLosesWith409() throws Exception {
        long ticket = createTicket("Claim me");

        send(post("/api/tickets/" + ticket + "/claim"), agentToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.technicianId").value(agent.getId()))
                .andExpect(jsonPath("$.data.status").value("assigned"));
        send(post("/api/tickets/" + ticket + "/claim"), agentToken, null).andExpect(status().isOk());
        send(post("/api/tickets/" + ticket + "/claim"), otherAgentToken, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        // Only agents claim; other tenants can't even see the ticket.
        send(post("/api/tickets/" + ticket + "/claim"), adminToken, null).andExpect(status().isForbidden());
        TestTenant other = testTenants.tenantWithAllRoles("Queue Other " + System.nanoTime());
        send(post("/api/tickets/" + ticket + "/claim"), other.token(SERVICE_AGENT), null).andExpect(status().isNotFound());

        // Once claimed, the owner can edit it.
        send(put("/api/tickets/" + ticket), agentToken,
                "{\"customerId\":" + customerId + ",\"subject\":\"Now mine\",\"priority\":\"high\"}").andExpect(status().isOk());
    }

    @Test
    void simultaneousClaims_exactlyOneWins() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                long ticket = createTicket("Race " + round);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> results = new ArrayList<>();
                for (String token : List.of(agentToken, otherAgentToken)) {
                    Callable<Integer> claim = () -> {
                        start.await();
                        return mockMvc.perform(post("/api/tickets/" + ticket + "/claim")
                                        .header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getStatus();
                    };
                    results.add(pool.submit(claim));
                }
                start.countDown();

                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> result : results) {
                    statuses.add(result.get(30, TimeUnit.SECONDS));
                }
                assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(200, 409);

                Long holder = ticketRepository.findById(ticket).orElseThrow().getAssignedTechnician().getId();
                assertThat(holder).isIn(agent.getId(), otherAgent.getId());
                assertThat(auditEventRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
                        tenant.tenant().getId(), "service_ticket", ticket))
                        .extracting(event -> event.getAction())
                        .containsExactly(AuditAction.TICKET_CLAIMED);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private long createTicket(String subject) throws Exception {
        return idOf(send(post("/api/tickets"), adminToken,
                "{\"customerId\":" + customerId + ",\"subject\":\"" + subject + "\",\"priority\":\"medium\"}"));
    }

    private void assign(long ticketId, User technician) throws Exception {
        send(patch("/api/tickets/" + ticketId + "/assign"), adminToken, "{\"technicianId\":" + technician.getId() + "}")
                .andExpect(status().isOk());
    }

    private void expectSubjects(MockHttpServletRequestBuilder request, String token, String... subjects) throws Exception {
        String body = send(request, token, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> actual = JsonPath.read(body, "$.data[*].subject");
        assertThat(actual).containsExactlyInAnyOrder(subjects);
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
