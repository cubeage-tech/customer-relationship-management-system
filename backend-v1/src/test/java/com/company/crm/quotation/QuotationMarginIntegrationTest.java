package com.company.crm.quotation;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.company.crm.common.enums.RoleType.ADMIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-4.8: estimated profit margin on a quotation, from product/line costs; never shown to the customer. */
class QuotationMarginIntegrationTest extends PostgresIntegrationTest {

    private TestTenant tenant;
    private String adminToken;
    private long customerId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Margin Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        customerId = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Buyer\",\"industry\":\"trading\"}"));
    }

    @Test
    void productsCarryAnOptionalCostPrice() throws Exception {
        send(post("/api/products"), adminToken, "{\"name\":\"Crane\",\"unitPrice\":1000,\"costPrice\":600}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.costPrice").value(600));
        send(post("/api/products"), adminToken, "{\"name\":\"Consulting\",\"unitPrice\":500}")
                .andExpect(jsonPath("$.data.costPrice").doesNotExist());
        send(post("/api/products"), adminToken, "{\"name\":\"Bad\",\"unitPrice\":5,\"costPrice\":-1}").andExpect(status().isBadRequest());
    }

    @Test
    void marginIsEstimatedWhenEveryLineHasACost() throws Exception {
        // 2 × 1000 at 10% off = 1800 (cost 1200) + 1 × 500 = 500 (cost 200) → total 2300, cost 1400, margin 900 (39.1%).
        long id = idOf(createQuotation("[" + line("Crane", 2, 1000, 10, "600") + "," + line("Install", 1, 500, 0, "200") + "]")
                .andExpect(jsonPath("$.data.grandTotal").value(2300))
                .andExpect(jsonPath("$.data.estimatedCost").value(1400))
                .andExpect(jsonPath("$.data.estimatedMargin").value(900))
                .andExpect(jsonPath("$.data.estimatedMarginPercent").value(39.1))
                .andExpect(jsonPath("$.data.linesWithoutCost").value(0))
                .andExpect(jsonPath("$.data.lineItems[0].unitCost").value(600)));

        // The customer's view link never shows cost or margin.
        String body = send(patch("/api/quotations/" + id + "/send"), adminToken, null).andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.data.viewUrl");
        mockMvc.perform(get("/api/public/quotations/" + url.substring(url.lastIndexOf('/') + 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineItems[0].unitCost").doesNotExist())
                .andExpect(jsonPath("$.data.estimatedMargin").doesNotExist())
                .andExpect(jsonPath("$.data.estimatedCost").doesNotExist());
    }

    @Test
    void marginIsUnknownWhileALineHasNoCost() throws Exception {
        createQuotation("[" + line("Crane", 1, 1000, 0, "600") + "," + line("Freight", 1, 100, 0, null) + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedCost").doesNotExist())
                .andExpect(jsonPath("$.data.estimatedMargin").doesNotExist())
                .andExpect(jsonPath("$.data.estimatedMarginPercent").doesNotExist())
                .andExpect(jsonPath("$.data.linesWithoutCost").value(1));
        // Selling below cost shows a negative margin rather than hiding it.
        createQuotation("[" + line("Clearance", 1, 100, 0, "150") + "]")
                .andExpect(jsonPath("$.data.estimatedMargin").value(-50))
                .andExpect(jsonPath("$.data.estimatedMarginPercent").value(-50.0));
        createQuotation("[" + line("Bad", 1, 100, 0, "-5") + "]").andExpect(status().isBadRequest());
    }

    private static String line(String name, int qty, int price, int discount, String cost) {
        return "{\"productName\":\"" + name + "\",\"quantity\":" + qty + ",\"unitPrice\":" + price
                + ",\"discountPercent\":" + discount + (cost != null ? ",\"unitCost\":" + cost : "") + "}";
    }

    private ResultActions createQuotation(String lineItems) throws Exception {
        return send(post("/api/quotations"), adminToken, "{\"customerId\":" + customerId + ",\"lineItems\":" + lineItems + "}");
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
