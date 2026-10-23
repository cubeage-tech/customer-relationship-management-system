package com.company.crm.sales;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.company.crm.common.enums.RoleType.*;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-3.2: tenant-configurable sales stages; Won and Lost stay fixed terminal stages. */
class PipelineStageConfigIntegrationTest extends PostgresIntegrationTest {

    private TestTenant tenant;
    private String adminToken;
    private long customerId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Stages Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        customerId = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Buyer\",\"industry\":\"trading\"}"));
    }

    @Test
    void adminConfiguresStagesAndOpportunitiesFollowThem() throws Exception {
        send(get("/api/opportunity-stages"), tenant.token(FINANCE_APPROVER), null)
                .andExpect(jsonPath("$.data[*].code").value(contains("qualification", "proposal", "negotiation", "won", "lost")))
                .andExpect(jsonPath("$.data[3].terminal").value(true));

        send(post("/api/opportunity-stages"), adminToken, "{\"label\":\"Site Visit\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("site_visit"))
                .andExpect(jsonPath("$.data.terminal").value(false));
        send(post("/api/opportunity-stages"), adminToken, "{\"label\":\"site visit\"}").andExpect(status().isConflict());
        send(post("/api/opportunity-stages"), tenant.token(SALES_MANAGER), "{\"label\":\"Demo\"}").andExpect(status().isForbidden());
        // New open stages go before Won/Lost.
        send(get("/api/opportunity-stages"), adminToken, null)
                .andExpect(jsonPath("$.data[*].code").value(contains("qualification", "proposal", "negotiation", "site_visit", "won", "lost")));

        // Reorder: new deals now start in the first open stage.
        send(put("/api/opportunity-stages/order"), adminToken, "[\"site_visit\",\"qualification\",\"proposal\",\"negotiation\"]")
                .andExpect(jsonPath("$.data[0].code").value("site_visit"));
        send(put("/api/opportunity-stages/order"), adminToken, "[\"site_visit\"]").andExpect(status().isBadRequest());
        long dealId = idOf(createOpportunity().andExpect(jsonPath("$.data.stage").value("site_visit")));

        moveTo(dealId, "proposal").andExpect(status().isOk());
        moveTo(dealId, "bogus").andExpect(status().isBadRequest());

        // Deactivated: no longer selectable, but deals already in it keep it and still show on the board.
        send(put("/api/opportunity-stages/proposal"), adminToken, "{\"active\":false}").andExpect(status().isOk());
        long other = idOf(createOpportunity());
        moveTo(other, "proposal").andExpect(status().isBadRequest());
        send(get("/api/opportunities/" + dealId), adminToken, null).andExpect(jsonPath("$.data.stage").value("proposal"));
        send(get("/api/opportunities/summary"), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.stage == 'proposal')].count").value(contains(1)));

        // Won/Lost: renamable, never removable; Lost still needs a reason.
        send(put("/api/opportunity-stages/won"), adminToken, "{\"active\":false}").andExpect(status().isBadRequest());
        send(put("/api/opportunity-stages/won"), adminToken, "{\"label\":\"Closed Won\"}").andExpect(jsonPath("$.data.label").value("Closed Won"));
        moveTo(other, "lost").andExpect(status().isBadRequest());
        moveTo(other, "won").andExpect(status().isOk());
        send(get("/api/opportunities/summary"), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.stage == 'won')].label").value(contains("Closed Won")))
                .andExpect(jsonPath("$.data[?(@.stage == 'won')].count").value(contains(1)));
        send(get("/api/opportunities/kpis"), adminToken, null).andExpect(status().isOk());

        // At least one open stage must stay active.
        send(put("/api/opportunity-stages/qualification"), adminToken, "{\"active\":false}").andExpect(status().isOk());
        send(put("/api/opportunity-stages/negotiation"), adminToken, "{\"active\":false}").andExpect(status().isOk());
        send(put("/api/opportunity-stages/site_visit"), adminToken, "{\"active\":false}").andExpect(status().isBadRequest());
    }

    @Test
    void stagesArePerTenantAndLeadConversionUsesTheFirstOpenStage() throws Exception {
        send(post("/api/opportunity-stages"), adminToken, "{\"label\":\"Discovery\"}").andExpect(status().isOk());
        send(put("/api/opportunity-stages/order"), adminToken, "[\"discovery\",\"qualification\",\"proposal\",\"negotiation\"]")
                .andExpect(status().isOk());

        long leadId = idOf(send(post("/api/leads"), adminToken,
                "{\"leadName\":\"Ravi\",\"companyName\":\"Conv Co\",\"industry\":\"trading\",\"source\":\"referral\"}"));
        send(patch("/api/leads/" + leadId + "/stage"), adminToken, "{\"stage\":\"converted\",\"force\":true}").andExpect(status().isOk());
        send(get("/api/opportunities").param("stage", "discovery"), adminToken, null).andExpect(jsonPath("$.data.length()").value(1));

        TestTenant other = testTenants.tenantWithAllRoles("Other Stages " + System.nanoTime());
        String otherAdmin = other.token(ADMIN);
        long otherCustomer = idOf(send(post("/api/customers"), otherAdmin, "{\"companyName\":\"X\",\"industry\":\"trading\"}"));
        long otherDeal = idOf(send(post("/api/opportunities"), otherAdmin, "{\"customerId\":" + otherCustomer + ",\"dealValue\":10}")
                .andExpect(jsonPath("$.data.stage").value("qualification")));
        send(patch("/api/opportunities/" + otherDeal + "/stage"), otherAdmin, "{\"stage\":\"discovery\"}").andExpect(status().isBadRequest());
    }

    private ResultActions createOpportunity() throws Exception {
        return send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customerId + ",\"dealValue\":100}");
    }

    private ResultActions moveTo(long id, String stage) throws Exception {
        return send(patch("/api/opportunities/" + id + "/stage"), adminToken, "{\"stage\":\"" + stage + "\"}");
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
