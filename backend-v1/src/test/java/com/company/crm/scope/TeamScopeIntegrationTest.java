package com.company.crm.scope;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Decision 3 end to end: a sales manager sees exactly their team's records once a team exists. */
class TeamScopeIntegrationTest extends PostgresIntegrationTest {

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

        Long teamId = testTenants.createTeam(tenant.tenant(), "North", tenant.user(SALES_MANAGER), inTeam);

        // Team exists → manager sees only the member's records.
        expectLeadNames(managerToken, "Insider lead");
        mockMvc.perform(get("/api/leads/" + insiderLead).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/leads/" + outsiderLead).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());

        // Admin (unchanged) and executives (own) are unaffected by teams.
        expectLeadNames(adminToken, "Insider lead", "Outsider lead");
        expectLeadNames(outsiderToken, "Outsider lead");

        // Removing the team restores the fallback.
        testTenants.deleteTeam(teamId);
        expectLeadNames(managerToken, "Insider lead", "Outsider lead");
    }

    private void expectLeadNames(String token, String... names) throws Exception {
        String body = mockMvc.perform(get("/api/leads").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> actual = JsonPath.read(body, "$.data[*].leadName");
        assertThat(actual).containsExactlyInAnyOrder(names);
    }

    private long createLead(String token, String name) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/leads").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"leadName\":\"" + name + "\",\"companyName\":\"Co\",\"industry\":\"trading\",\"source\":\"referral\"}");
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
