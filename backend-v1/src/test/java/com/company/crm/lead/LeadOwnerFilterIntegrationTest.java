package com.company.crm.lead;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.company.crm.common.enums.RoleType.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-2.7: filter leads by assigned owner, always within the caller's data scope. */
class LeadOwnerFilterIntegrationTest extends PostgresIntegrationTest {

    private TestTenant tenant;
    private String adminToken;
    private User exec;
    private User manager;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Owner Filter Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        exec = tenant.user(SALES_EXECUTIVE);
        manager = tenant.user(SALES_MANAGER);
        createLead("Exec lead 1", exec.getId());
        createLead("Exec lead 2", exec.getId());
        createLead("Manager lead", manager.getId());
    }

    @Test
    void filtersByOwner() throws Exception {
        send(get("/api/leads"), adminToken).andExpect(jsonPath("$.data.length()").value(3));
        send(get("/api/leads").param("ownerId", exec.getId().toString()), adminToken)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].ownerId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(exec.getId().intValue()))));
        send(get("/api/leads").param("ownerId", manager.getId().toString()).param("search", "manager"), adminToken)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].leadName").value("Manager lead"));
    }

    @Test
    void ownerFilterNeverWidensScope() throws Exception {
        // A sales executive asking for someone else's leads gets nothing, not those leads.
        send(get("/api/leads").param("ownerId", manager.getId().toString()), tenant.token(SALES_EXECUTIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        // Another tenant's admin filtering by this tenant's user sees nothing.
        TestTenant other = testTenants.tenantWithAllRoles("Other Filter " + System.nanoTime());
        send(get("/api/leads").param("ownerId", exec.getId().toString()), other.token(ADMIN))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private void createLead(String name, Long ownerId) throws Exception {
        mockMvc.perform(post("/api/leads").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leadName\":\"" + name + "\",\"companyName\":\"Co\",\"industry\":\"trading\",\"source\":\"referral\",\"ownerId\":" + ownerId + "}"))
                .andExpect(status().isOk());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }
}
