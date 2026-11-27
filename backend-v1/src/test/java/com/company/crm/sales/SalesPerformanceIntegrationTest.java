package com.company.crm.sales;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.company.crm.common.enums.RoleType.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-3.5: open/won/lost opportunity value per salesperson and per team, within the caller's scope. */
class SalesPerformanceIntegrationTest extends PostgresIntegrationTest {

    private TestTenant tenant;
    private String adminToken;
    private User execA;
    private User execB;
    private User execC;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Performance Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        execA = tenant.user(SALES_EXECUTIVE);
        execB = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        execC = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE); // in no team
        long teamId = idOf(send(post("/api/sales-teams"), adminToken,
                "{\"name\":\"West\",\"managerId\":" + tenant.user(SALES_MANAGER).getId() + "}"));
        send(put("/api/sales-teams/" + teamId + "/members"), adminToken,
                "{\"userIds\":[" + execA.getId() + "," + execB.getId() + "]}").andExpect(status().isOk());

        long customerId = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Buyer\",\"industry\":\"trading\"}"));
        opportunity(customerId, execA, 100, null);
        opportunity(customerId, execA, 200, "won");
        opportunity(customerId, execB, 300, "negotiation");
        opportunity(customerId, execB, 50, "lost");
        opportunity(customerId, execC, 1000, "won");
    }

    @Test
    void adminSeesEveryoneAndEveryTeam() throws Exception {
        send(get("/api/opportunities/performance"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.byOwner.length()").value(3))
                // Sorted by won value: C (1000), A (200), B (0).
                .andExpect(jsonPath("$.data.byOwner[0].id").value(execC.getId()))
                .andExpect(jsonPath("$.data.byOwner[1].id").value(execA.getId()))
                .andExpect(jsonPath("$.data.byOwner[1].openCount").value(1))
                .andExpect(jsonPath("$.data.byOwner[1].openValue").value(100))
                .andExpect(jsonPath("$.data.byOwner[1].wonValue").value(200))
                .andExpect(jsonPath("$.data.byOwner[2].openValue").value(300))
                .andExpect(jsonPath("$.data.byOwner[2].lostCount").value(1))
                .andExpect(jsonPath("$.data.byOwner[2].lostValue").value(50))
                .andExpect(jsonPath("$.data.byTeam.length()").value(2))
                .andExpect(jsonPath("$.data.byTeam[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.byTeam[0].name").value("No team"))
                .andExpect(jsonPath("$.data.byTeam[0].wonValue").value(1000))
                .andExpect(jsonPath("$.data.byTeam[1].name").value("West"))
                .andExpect(jsonPath("$.data.byTeam[1].openCount").value(2))
                .andExpect(jsonPath("$.data.byTeam[1].openValue").value(400))
                .andExpect(jsonPath("$.data.byTeam[1].wonValue").value(200))
                .andExpect(jsonPath("$.data.byTeam[1].lostValue").value(50));
    }

    @Test
    void viewsFollowDataScope() throws Exception {
        // The team manager sees only their team's people.
        send(get("/api/opportunities/performance"), tenant.token(SALES_MANAGER), null)
                .andExpect(jsonPath("$.data.byOwner.length()").value(2))
                .andExpect(jsonPath("$.data.byTeam.length()").value(1))
                .andExpect(jsonPath("$.data.byTeam[0].name").value("West"));
        // A sales executive sees only themself.
        send(get("/api/opportunities/performance"), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(jsonPath("$.data.byOwner.length()").value(1))
                .andExpect(jsonPath("$.data.byOwner[0].id").value(execA.getId()));
        // Another tenant sees none of it; roles without opportunity access are refused.
        TestTenant other = testTenants.tenantWithAllRoles("Other Performance " + System.nanoTime());
        send(get("/api/opportunities/performance"), other.token(ADMIN), null)
                .andExpect(jsonPath("$.data.byOwner.length()").value(0));
        send(get("/api/opportunities/performance"), tenant.token(MARKETING_EXECUTIVE), null).andExpect(status().isForbidden());
    }

    private void opportunity(long customerId, User owner, int value, String stage) throws Exception {
        long id = idOf(send(post("/api/opportunities"), adminToken,
                "{\"customerId\":" + customerId + ",\"dealValue\":" + value + ",\"ownerId\":" + owner.getId() + "}"));
        if (stage != null) {
            String reason = "lost".equals(stage) ? ",\"lossReason\":\"Price\"" : "";
            send(patch("/api/opportunities/" + id + "/stage"), adminToken, "{\"stage\":\"" + stage + "\",\"force\":true" + reason + "}")
                    .andExpect(status().isOk());
        }
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
