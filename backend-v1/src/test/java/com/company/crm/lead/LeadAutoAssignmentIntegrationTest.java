package com.company.crm.lead;

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

/** FR-2.3: configurable auto-assignment rules for new leads (named user, or team round-robin). */
class LeadAutoAssignmentIntegrationTest extends PostgresIntegrationTest {

    private TestTenant tenant;
    private String adminToken;
    private User execA;
    private User execB;
    private long teamId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Auto Assign Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        execA = tenant.user(SALES_EXECUTIVE);
        execB = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        teamId = idOf(send(post("/api/sales-teams"), adminToken,
                "{\"name\":\"West\",\"managerId\":" + tenant.user(SALES_MANAGER).getId() + "}"));
        send(put("/api/sales-teams/" + teamId + "/members"), adminToken,
                "{\"userIds\":[" + execA.getId() + "," + execB.getId() + "]}").andExpect(status().isOk());
    }

    @Test
    void firstMatchingRuleAssignsOwnerOrRotatesWithinTeam() throws Exception {
        // Priority 10: exhibition leads go to the sales manager. Priority 20: healthcare round-robins in West.
        send(post("/api/lead-assignment-rules"), adminToken, "{\"name\":\"Expo\",\"priority\":10,\"matchSource\":\"exhibition\",\"assignUserId\":"
                + tenant.user(SALES_MANAGER).getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignUserName").isNotEmpty());
        send(post("/api/lead-assignment-rules"), adminToken, "{\"name\":\"Health\",\"priority\":20,\"matchIndustry\":\"healthcare\",\"assignTeamId\":" + teamId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignTeamName").value("West"));

        // Admin creates without picking an owner, so rules apply.
        String creator = adminToken;
        // Matches both rules — lower priority wins.
        createLead(creator, "healthcare", "exhibition", null)
                .andExpect(jsonPath("$.data.ownerId").value(tenant.user(SALES_MANAGER).getId()))
                .andExpect(jsonPath("$.data.teamId").doesNotExist());
        // Team rule: owners alternate A, B, A; the lead gets the team.
        createLead(creator, "healthcare", "website", null)
                .andExpect(jsonPath("$.data.ownerId").value(execA.getId()))
                .andExpect(jsonPath("$.data.teamId").value(teamId));
        createLead(creator, "healthcare", "website", null).andExpect(jsonPath("$.data.ownerId").value(execB.getId()));
        createLead(creator, "healthcare", "website", null).andExpect(jsonPath("$.data.ownerId").value(execA.getId()));
        // No rule matches: unchanged behaviour (creator owns it).
        createLead(creator, "software", "website", null)
                .andExpect(jsonPath("$.data.ownerId").value(tenant.user(ADMIN).getId()));
        // An explicit owner, or a sales executive's own lead, is never overridden.
        createLead(adminToken, "healthcare", "website", tenant.user(SALES_MANAGER).getId())
                .andExpect(jsonPath("$.data.ownerId").value(tenant.user(SALES_MANAGER).getId()));
        createLead(tenant.token(SALES_EXECUTIVE), "healthcare", "exhibition", null)
                .andExpect(jsonPath("$.data.ownerId").value(execA.getId()))
                .andExpect(jsonPath("$.data.teamId").doesNotExist());
    }

    @Test
    void inactiveRulesAreSkippedAndRulesAreAdminOnly() throws Exception {
        long ruleId = idOf(send(post("/api/lead-assignment-rules"), adminToken,
                "{\"name\":\"All to B\",\"assignUserId\":" + execB.getId() + "}"));
        createLead(adminToken, "trading", "referral", null).andExpect(jsonPath("$.data.ownerId").value(execB.getId()));

        send(put("/api/lead-assignment-rules/" + ruleId), adminToken,
                "{\"name\":\"All to B\",\"active\":false,\"assignUserId\":" + execB.getId() + "}")
                .andExpect(jsonPath("$.data.active").value(false));
        createLead(adminToken, "trading", "referral", null).andExpect(jsonPath("$.data.ownerId").value(tenant.user(ADMIN).getId()));

        send(get("/api/lead-assignment-rules"), adminToken, null).andExpect(jsonPath("$.data.length()").value(1));
        send(delete("/api/lead-assignment-rules/" + ruleId), adminToken, null).andExpect(status().isOk());
        send(get("/api/lead-assignment-rules"), adminToken, null).andExpect(jsonPath("$.data.length()").value(0));

        // Validation: exactly one target, a sales user, of this tenant; known source.
        send(post("/api/lead-assignment-rules"), adminToken, "{\"name\":\"X\"}").andExpect(status().isBadRequest());
        send(post("/api/lead-assignment-rules"), adminToken,
                "{\"name\":\"X\",\"assignUserId\":" + execA.getId() + ",\"assignTeamId\":" + teamId + "}").andExpect(status().isBadRequest());
        send(post("/api/lead-assignment-rules"), adminToken,
                "{\"name\":\"X\",\"assignUserId\":" + tenant.user(FINANCE_APPROVER).getId() + "}").andExpect(status().isBadRequest());
        send(post("/api/lead-assignment-rules"), adminToken,
                "{\"name\":\"X\",\"matchSource\":\"carrier_pigeon\",\"assignUserId\":" + execA.getId() + "}").andExpect(status().isBadRequest());
        TestTenant other = testTenants.tenantWithAllRoles("Other Assign " + System.nanoTime());
        send(post("/api/lead-assignment-rules"), other.token(ADMIN), "{\"name\":\"X\",\"assignTeamId\":" + teamId + "}").andExpect(status().isBadRequest());

        send(get("/api/lead-assignment-rules"), tenant.token(SALES_MANAGER), null).andExpect(status().isForbidden());
        send(post("/api/lead-assignment-rules"), tenant.token(SALES_MANAGER), "{\"name\":\"X\",\"assignUserId\":" + execA.getId() + "}")
                .andExpect(status().isForbidden());
    }

    private ResultActions createLead(String token, String industry, String source, Long ownerId) throws Exception {
        String owner = ownerId != null ? ",\"ownerId\":" + ownerId : "";
        return send(post("/api/leads"), token, "{\"leadName\":\"Ravi\",\"companyName\":\"Lead Co\",\"industry\":\"" + industry
                + "\",\"source\":\"" + source + "\"" + owner + "}").andExpect(status().isOk());
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
