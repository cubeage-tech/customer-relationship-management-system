package com.company.crm.lead;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.company.crm.common.enums.RoleType.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-2.3: a lead can be assigned to a sales team; that team's manager then sees it. */
class LeadTeamAssignmentIntegrationTest extends PostgresIntegrationTest {

    private TestTenant tenant;
    private String adminToken;
    private String managerToken;
    private long teamId;
    private long leadId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Team Leads " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        managerToken = tenant.token(SALES_MANAGER);
        teamId = idOf(send(post("/api/sales-teams"), adminToken,
                "{\"name\":\"North\",\"managerId\":" + tenant.user(SALES_MANAGER).getId() + "}"));
        // Owned by the admin, so outside the manager's TEAM scope until it is assigned to their team.
        leadId = idOf(send(post("/api/leads"), adminToken,
                "{\"leadName\":\"Ravi\",\"companyName\":\"XYZ Industries\",\"industry\":\"manufacturing\",\"source\":\"referral\"}"));
    }

    @Test
    void assigningToATeamPutsTheLeadInThatManagersQueue() throws Exception {
        send(get("/api/leads/" + leadId), managerToken, null).andExpect(status().isForbidden());
        send(get("/api/leads"), managerToken, null).andExpect(jsonPath("$.data[?(@.id == " + leadId + ")]").isEmpty());

        // The manager can pick from the tenant's teams.
        send(get("/api/leads/assignable-teams"), managerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(Matchers.hasItem("North")));

        long ownerBefore = tenant.user(ADMIN).getId();
        send(patch("/api/leads/" + leadId + "/team"), adminToken, "{\"teamId\":" + teamId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(teamId))
                .andExpect(jsonPath("$.data.teamName").value("North"))
                .andExpect(jsonPath("$.data.ownerId").value(ownerBefore)); // owner is kept

        send(get("/api/leads/" + leadId), managerToken, null).andExpect(status().isOk());
        send(get("/api/leads"), managerToken, null).andExpect(jsonPath("$.data[?(@.id == " + leadId + ")]").isNotEmpty());

        // Clearing the team takes it out of the manager's queue again.
        send(patch("/api/leads/" + leadId + "/team"), adminToken, "{\"teamId\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").doesNotExist());
        send(get("/api/leads/" + leadId), managerToken, null).andExpect(status().isForbidden());
    }

    @Test
    void validationRolesAndTeamDeletion() throws Exception {
        // A team from another tenant is rejected.
        TestTenant other = testTenants.tenantWithAllRoles("Other Team Leads " + System.nanoTime());
        long foreignTeam = idOf(send(post("/api/sales-teams"), other.token(ADMIN), "{\"name\":\"South\"}"));
        send(patch("/api/leads/" + leadId + "/team"), adminToken, "{\"teamId\":" + foreignTeam + "}").andExpect(status().isBadRequest());

        // Only admin / sales manager may assign teams.
        send(patch("/api/leads/" + leadId + "/team"), tenant.token(SALES_EXECUTIVE), "{\"teamId\":" + teamId + "}").andExpect(status().isForbidden());
        send(get("/api/leads/assignable-teams"), tenant.token(SALES_EXECUTIVE), null).andExpect(status().isForbidden());

        // Deleting the team unassigns its leads instead of failing.
        send(patch("/api/leads/" + leadId + "/team"), adminToken, "{\"teamId\":" + teamId + "}").andExpect(status().isOk());
        send(delete("/api/sales-teams/" + teamId), adminToken, null).andExpect(status().isOk());
        send(get("/api/leads/" + leadId), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").doesNotExist());
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
