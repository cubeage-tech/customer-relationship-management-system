package com.company.crm.dashboard;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static com.company.crm.common.enums.RoleType.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every dashboard summary endpoint, end to end over HTTP against PostgreSQL: the DB aggregates
 * return the right numbers, and each respects the caller's data scope (own / team / tenant).
 */
class DashboardSummaryIntegrationTest extends PostgresIntegrationTest {

    @Test
    void summariesAggregateCorrectlyAndRespectScope() throws Exception {
        TestTenant tenant = testTenants.tenantWithAllRoles("Dash Co " + System.nanoTime());
        String admin = tenant.token(ADMIN);
        String exec = tenant.token(SALES_EXECUTIVE);
        String finance = tenant.token(FINANCE_APPROVER);
        User execUser = tenant.user(SALES_EXECUTIVE);

        // ---- Data: leads (2 exec, 1 admin), customers (1 each), opportunities, quotations, a ticket
        createLead(exec, "Exec lead 1");
        createLead(exec, "Exec lead 2");
        createLead(admin, "Admin lead");
        long adminCustomer = id(send(post("/api/customers"), admin, "{\"companyName\":\"Admin Co\",\"industry\":\"trading\"}"));
        long execCustomer = id(send(post("/api/customers"), admin,
                "{\"companyName\":\"Exec Co\",\"industry\":\"trading\",\"ownerId\":" + execUser.getId() + "}"));

        LocalDate today = LocalDate.now();
        id(send(post("/api/opportunities"), exec, "{\"customerId\":" + execCustomer + ",\"dealValue\":1000,\"expectedClosingDate\":\"" + today + "\"}"));
        long wonOpp = id(send(post("/api/opportunities"), exec, "{\"customerId\":" + execCustomer + ",\"dealValue\":500}"));
        send(patch("/api/opportunities/" + wonOpp + "/stage"), exec, "{\"stage\":\"won\"}").andExpect(status().isOk());
        id(send(post("/api/opportunities"), admin, "{\"customerId\":" + adminCustomer + ",\"dealValue\":2000,\"expectedClosingDate\":\"" + today.plusDays(40) + "\"}"));

        id(send(post("/api/quotations"), exec, quotation(execCustomer, 1, 100, 0)));                  // draft, no discount
        long discounted = id(send(post("/api/quotations"), exec, quotation(execCustomer, 2, 500, 20))); // 20% > 15% threshold

        // ---- Quotations: pending approval before, approved after (gross 1000, net 800 → 20%)
        send(get("/api/quotations/summary"), exec, null)
                .andExpect(jsonPath("$.data.draft").value(2))
                .andExpect(jsonPath("$.data.pendingApproval").value(1))
                .andExpect(jsonPath("$.data.approvedCount").value(0))
                .andExpect(jsonPath("$.data.averageDiscountPercent").doesNotExist());
        send(patch("/api/quotations/" + discounted + "/discount/approve"), finance, "{}").andExpect(status().isOk());
        send(get("/api/quotations/summary"), finance, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingApproval").value(0))
                .andExpect(jsonPath("$.data.approvedCount").value(1))
                .andExpect(jsonPath("$.data.approvedTotal").value(800.00))
                .andExpect(jsonPath("$.data.averageDiscountPercent").value(20.00));

        // ---- Leads: exec sees own, admin the tenant
        send(get("/api/leads/summary"), exec, null)
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.open").value(2))
                .andExpect(jsonPath("$.data.newThisMonth").value(2));
        send(get("/api/leads/summary"), admin, null).andExpect(jsonPath("$.data.total").value(3));

        // ---- Customers
        send(get("/api/customers/summary"), admin, null)
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.active").value(2))
                .andExpect(jsonPath("$.data.newThisQuarter").value(2));
        send(get("/api/customers/summary"), exec, null).andExpect(jsonPath("$.data.total").value(1));

        // ---- Opportunity KPIs
        send(get("/api/opportunities/kpis"), exec, null)
                .andExpect(jsonPath("$.data.openCount").value(1))
                .andExpect(jsonPath("$.data.openValue").value(1000))
                .andExpect(jsonPath("$.data.closingThisWeek").value(1))
                .andExpect(jsonPath("$.data.wonThisMonthCount").value(1))
                .andExpect(jsonPath("$.data.wonThisMonthValue").value(500))
                .andExpect(jsonPath("$.data.wonThisQuarterValue").value(500));
        send(get("/api/opportunities/kpis"), admin, null)
                .andExpect(jsonPath("$.data.openCount").value(2))
                .andExpect(jsonPath("$.data.openValue").value(3000))
                .andExpect(jsonPath("$.data.closingThisWeek").value(1));
        // Existing stage summary keeps its contract: every stage, zero-filled.
        send(get("/api/opportunities/summary"), admin, null)
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[?(@.stage=='won')].count").value(1))
                .andExpect(jsonPath("$.data[?(@.stage=='qualification')].totalValue").value(3000.0));

        // ---- Tickets: a critical ticket is due within 24h; resolving it moves it to resolved this week
        long ticket = id(send(post("/api/tickets"), admin, "{\"customerId\":" + adminCustomer + ",\"subject\":\"Down\",\"priority\":\"critical\"}"));
        send(get("/api/tickets/summary"), admin, null)
                .andExpect(jsonPath("$.data.open").value(1))
                .andExpect(jsonPath("$.data.dueWithin24h").value(1))
                .andExpect(jsonPath("$.data.onTrack").value(1))
                .andExpect(jsonPath("$.data.breached").value(0))
                .andExpect(jsonPath("$.data.averageResolutionHours").doesNotExist());
        send(patch("/api/tickets/" + ticket + "/status"), admin, "{\"status\":\"resolved\"}").andExpect(status().isOk());
        send(get("/api/tickets/summary"), admin, null)
                .andExpect(jsonPath("$.data.open").value(0))
                .andExpect(jsonPath("$.data.resolvedThisWeek").value(1))
                .andExpect(jsonPath("$.data.averageResolutionHours").value(0.0));
        // Agent summary only counts tickets assigned to them.
        send(get("/api/tickets/summary"), tenant.token(SERVICE_AGENT), null).andExpect(jsonPath("$.data.resolvedThisWeek").value(0));

        // ---- Team scope applies to summaries too: manager of a team containing the exec
        testTenants.createTeam(tenant.tenant(), "Dash team", tenant.user(SALES_MANAGER), execUser);
        send(get("/api/leads/summary"), tenant.token(SALES_MANAGER), null).andExpect(jsonPath("$.data.total").value(2));
        send(get("/api/opportunities/kpis"), tenant.token(SALES_MANAGER), null).andExpect(jsonPath("$.data.openValue").value(1000));
    }

    private static String quotation(long customerId, int quantity, int unitPrice, int discountPercent) {
        return "{\"customerId\":" + customerId + ",\"lineItems\":[{\"productName\":\"Widget\",\"quantity\":" + quantity
                + ",\"unitPrice\":" + unitPrice + ",\"discountPercent\":" + discountPercent + "}]}";
    }

    private void createLead(String token, String name) throws Exception {
        id(send(post("/api/leads"), token,
                "{\"leadName\":\"" + name + "\",\"companyName\":\"Co\",\"industry\":\"trading\",\"source\":\"referral\"}"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private long id(ResultActions result) throws Exception {
        String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
