package com.company.crm.scope;

import com.company.crm.common.audit.AuditEventRepository;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Decision 3 end to end: a sales manager sees exactly their team's records once a team exists. */
class TeamScopeIntegrationTest extends PostgresIntegrationTest {

    @Autowired private AuditEventRepository auditEventRepository;

    @Test
    void salesManagerSeesTeamRecordsOnly_adminStillSeesAll() throws Exception {
        TestTenant tenant = testTenants.tenantWithAllRoles("Teams Co " + System.nanoTime());
        User inTeam = tenant.user(SALES_EXECUTIVE);
        User outsider = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        String insiderToken = tenant.token(SALES_EXECUTIVE);
        String outsiderToken = testTenants.tokenFor(outsider);
        String managerToken = tenant.token(SALES_MANAGER);
        String adminToken = tenant.token(ADMIN);

        long insiderLead = createLead(insiderToken, "Insider lead");
        long outsiderLead = createLead(outsiderToken, "Outsider lead");

        // No team yet → manager falls back to tenant-wide visibility.
        expectLeadNames(managerToken, "Insider lead", "Outsider lead");

        long teamId = idOf(send(post("/api/sales-teams"), adminToken,
                "{\"name\":\"North\",\"managerId\":" + tenant.user(SALES_MANAGER).getId() + "}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        send(put("/api/sales-teams/" + teamId + "/members"), adminToken, "{\"userIds\":[" + inTeam.getId() + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[0].id").value(inTeam.getId()));

        // Team exists → manager sees only the member's records.
        expectLeadNames(managerToken, "Insider lead");
        mockMvc.perform(get("/api/leads/" + insiderLead).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/leads/" + outsiderLead).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());

        // Admin (unchanged) and executives (own) are unaffected by teams.
        expectLeadNames(adminToken, "Insider lead", "Outsider lead");
        expectLeadNames(outsiderToken, "Outsider lead");

        // Team changes are audited.
        assertThat(auditEventRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
                tenant.tenant().getId(), "sales_team", teamId))
                .extracting(event -> event.getAction().name())
                .containsExactly("TEAM_CREATED", "TEAM_MEMBERS_CHANGED");

        // Deleting the team restores the fallback.
        send(delete("/api/sales-teams/" + teamId), adminToken, "").andExpect(status().isOk());
        expectLeadNames(managerToken, "Insider lead", "Outsider lead");
    }

    @Test
    void teamValidationAndAccess() throws Exception {
        TestTenant tenant = testTenants.tenantWithAllRoles("Teams Rules " + System.nanoTime());
        String adminToken = tenant.token(ADMIN);

        // Only the tenant admin manages teams.
        send(get("/api/sales-teams"), tenant.token(SALES_MANAGER), "").andExpect(status().isForbidden());
        // Manager must be a sales manager.
        send(post("/api/sales-teams"), adminToken, "{\"name\":\"Bad\",\"managerId\":" + tenant.user(FINANCE_APPROVER).getId() + "}")
                .andExpect(status().isBadRequest());
        // Names are unique per tenant.
        send(post("/api/sales-teams"), adminToken, "{\"name\":\"East\"}").andExpect(status().isOk());
        send(post("/api/sales-teams"), adminToken, "{\"name\":\"east\"}").andExpect(status().isConflict());
        // Blank name rejected by validation.
        send(post("/api/sales-teams"), adminToken, "{\"name\":\" \"}").andExpect(status().isBadRequest());

        // Members must be sales executives of this tenant.
        long teamId = idOf(send(post("/api/sales-teams"), adminToken, "{\"name\":\"West\"}")
                .andReturn().getResponse().getContentAsString());
        send(put("/api/sales-teams/" + teamId + "/members"), adminToken,
                "{\"userIds\":[" + tenant.user(MARKETING_EXECUTIVE).getId() + "]}").andExpect(status().isBadRequest());
        TestTenant other = testTenants.tenantWithAllRoles("Teams Other " + System.nanoTime());
        send(put("/api/sales-teams/" + teamId + "/members"), adminToken,
                "{\"userIds\":[" + other.user(SALES_EXECUTIVE).getId() + "]}").andExpect(status().isBadRequest());
        // Another tenant's team is invisible.
        send(put("/api/sales-teams/" + teamId), other.token(ADMIN), "{\"name\":\"Hijack\"}").andExpect(status().isNotFound());
    }

    private void expectLeadNames(String token, String... names) throws Exception {
        String body = mockMvc.perform(get("/api/leads").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> actual = JsonPath.read(body, "$.data[*].leadName");
        assertThat(actual).containsExactlyInAnyOrder(names);
    }

    private long createLead(String token, String name) throws Exception {
        return idOf(send(post("/api/leads"), token,
                "{\"leadName\":\"" + name + "\",\"companyName\":\"Co\",\"industry\":\"trading\",\"source\":\"referral\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions send(MockHttpServletRequestBuilder request, String token,
                                                                   String body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long idOf(String body) {
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
