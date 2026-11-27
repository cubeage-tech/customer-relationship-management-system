package com.company.crm.customer;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
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

/** FR-1.3: a customer's purchase history = won deals + customer-approved quotations, newest first. */
class CustomerPurchaseHistoryIntegrationTest extends PostgresIntegrationTest {

    private TestTenant tenant;
    private String adminToken;
    private long customerId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Purchases Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        customerId = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"ABC Engineering\",\"industry\":\"automobile\"}"));
    }

    @Test
    void listsWonDealsAndApprovedQuotations_newestFirst_withoutDuplicates() throws Exception {
        // A won deal.
        long dealId = idOf(send(post("/api/opportunities"), adminToken,
                "{\"customerId\":" + customerId + ",\"productService\":\"Industrial Crane\",\"dealValue\":2500000}"));
        send(patch("/api/opportunities/" + dealId + "/stage"), adminToken, "{\"stage\":\"won\",\"force\":true}").andExpect(status().isOk());

        // A quotation on that won deal is the same purchase — must not be listed twice.
        long dealQuote = createQuotation(dealId, "Industrial Crane", "2500000");
        approve(dealQuote);

        // A stand-alone approved quotation (the newest purchase).
        long standalone = createQuotation(null, "Maintenance Contract", "100000");
        approve(standalone);

        // Neither a pending quotation nor an open deal is a purchase.
        createQuotation(null, "Spare Parts", "5000");
        send(post("/api/opportunities"), adminToken,
                "{\"customerId\":" + customerId + ",\"productService\":\"Open deal\",\"dealValue\":1000}").andExpect(status().isOk());

        send(get("/api/customers/" + customerId + "/purchases"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].source").value("quotation"))
                .andExpect(jsonPath("$.data[0].sourceId").value(standalone))
                .andExpect(jsonPath("$.data[0].productService").value("Maintenance Contract"))
                .andExpect(jsonPath("$.data[0].orderValue").value(100000.0))
                .andExpect(jsonPath("$.data[0].purchasedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[1].source").value("opportunity"))
                .andExpect(jsonPath("$.data[1].sourceId").value(dealId))
                .andExpect(jsonPath("$.data[1].productService").value("Industrial Crane"))
                .andExpect(jsonPath("$.data[1].orderValue").value(2500000.0));

        // A withdrawn approval drops out of the history.
        send(patch("/api/quotations/" + standalone + "/customer-status"), adminToken, "{\"status\":\"rejected\"}").andExpect(status().isOk());
        send(get("/api/customers/" + customerId + "/purchases"), adminToken, null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].source").value("opportunity"));
    }

    @Test
    void respectsTenantAndDataScope() throws Exception {
        // Another tenant cannot see the customer at all.
        TestTenant other = testTenants.tenantWithAllRoles("Other Co " + System.nanoTime());
        send(get("/api/customers/" + customerId + "/purchases"), other.token(ADMIN), null).andExpect(status().isNotFound());

        // A sales executive only sees purchases of customers they own (own data scope).
        send(get("/api/customers/" + customerId + "/purchases"), tenant.token(SALES_EXECUTIVE), null).andExpect(status().isForbidden());

        // Every tenant role with access can read it; a customer with no purchases returns an empty list.
        send(get("/api/customers/" + customerId + "/purchases"), tenant.token(FINANCE_APPROVER), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private long createQuotation(Long opportunityId, String product, String unitPrice) throws Exception {
        return idOf(send(post("/api/quotations"), adminToken,
                "{\"customerId\":" + customerId + (opportunityId != null ? ",\"opportunityId\":" + opportunityId : "")
                        + ",\"lineItems\":[{\"productName\":\"" + product + "\",\"quantity\":1,\"unitPrice\":" + unitPrice + ",\"discountPercent\":0}]}"));
    }

    private void approve(long quotationId) throws Exception {
        send(patch("/api/quotations/" + quotationId + "/customer-status"), adminToken, "{\"status\":\"approved\"}").andExpect(status().isOk());
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
