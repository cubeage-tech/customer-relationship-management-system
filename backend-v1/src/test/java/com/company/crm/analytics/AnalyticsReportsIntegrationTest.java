package com.company.crm.analytics;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static com.company.crm.common.enums.RoleType.*;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Module 9: FR-9.1 headline + configuration, FR-9.2 sales, FR-9.3 customer, FR-9.4 revenue and
 * FR-9.5 team performance reports, and FR-9.6 filters within the caller's data scope.
 */
class AnalyticsReportsIntegrationTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private TestTenant tenant;
    private String adminToken;
    private String managerToken;
    private User execA;
    private User execB;
    private User outsider; // sales executive in no team
    private long teamId;
    private final String today = LocalDate.now().toString();
    private final String yesterday = LocalDate.now().minusDays(1).toString();

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Analytics Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        managerToken = tenant.token(SALES_MANAGER);
        execA = tenant.user(SALES_EXECUTIVE);
        execB = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        outsider = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        teamId = idOf(send(post("/api/sales-teams"), adminToken, "{\"name\":\"West\",\"managerId\":" + tenant.user(SALES_MANAGER).getId() + "}"));
        send(put("/api/sales-teams/" + teamId + "/members"), adminToken, "{\"userIds\":[" + execA.getId() + "," + execB.getId() + "]}")
                .andExpect(status().isOk());

        // Today: execA has 2 leads (1 converted) and wins 4000 (Kit, trading); execB wins 1000 (software);
        // outsider wins 500; an open 2000 deal for execA. Yesterday: execA had 1 lead and won 2000.
        long tradingA = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Trader\",\"industry\":\"trading\",\"ownerId\":" + execA.getId() + "}"));
        long softwareB = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Coder\",\"industry\":\"software\",\"ownerId\":" + execB.getId() + "}"));
        long otherC = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Lone\",\"industry\":\"trading\",\"ownerId\":" + outsider.getId() + "}"));
        long converted = lead(execA, "trading");
        send(patch("/api/leads/" + converted + "/stage"), adminToken, "{\"stage\":\"converted\",\"force\":true}").andExpect(status().isOk());
        lead(execA, "trading");
        long old = lead(execA, "trading");
        jdbc.update("UPDATE leads SET created_at = ?::date + time '12:00' WHERE id = ?", yesterday, old);

        won(tradingA, execA, 4000, "Kit");
        won(softwareB, execB, 1000, null);
        won(otherC, outsider, 500, "Kit");
        long earlier = won(tradingA, execA, 2000, "Kit");
        jdbc.update("UPDATE opportunities SET stage_changed_at = ?::date + time '12:00' WHERE id = ?", yesterday, earlier);
        idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + tradingA + ",\"dealValue\":2000,\"ownerId\":" + execA.getId() + "}"));

        send(post("/api/communications/calls"), tenant.token(SALES_EXECUTIVE), "{\"customerId\":" + tradingA + ",\"direction\":\"outbound\",\"outcome\":\"connected\"}")
                .andExpect(status().isOk());
    }

    @Test
    void headlineComparesWithThePreviousPeriodAndCanBeConfigured() throws Exception {
        send(get("/api/analytics/headline").param("from", today).param("to", today), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current.totalLeads").value(2))
                .andExpect(jsonPath("$.data.current.convertedLeads").value(1))
                .andExpect(jsonPath("$.data.current.conversionRate").value(50.0))
                .andExpect(jsonPath("$.data.current.revenue").value(5500.0))
                .andExpect(jsonPath("$.data.current.wonDeals").value(3))
                .andExpect(jsonPath("$.data.previous.totalLeads").value(1))
                .andExpect(jsonPath("$.data.previous.revenue").value(2000.0))
                .andExpect(jsonPath("$.data.leadGrowthPercent").value(100.0))
                .andExpect(jsonPath("$.data.revenueGrowthPercent").value(175.0))
                .andExpect(jsonPath("$.data.openPipelineValue").value(2000.0))
                .andExpect(jsonPath("$.data.generatedAt").exists());

        // A salesperson's headline only counts their own records.
        send(get("/api/analytics/headline").param("from", today).param("to", today), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(jsonPath("$.data.current.revenue").value(4000.0))
                .andExpect(jsonPath("$.data.revenueGrowthPercent").value(100.0));

        // Configuration: defaults, a saved choice, and validation.
        String exec = tenant.token(SALES_EXECUTIVE);
        send(get("/api/analytics/dashboard-preferences"), exec, null)
                .andExpect(jsonPath("$.data.period").value("month"))
                .andExpect(jsonPath("$.data.widgets.length()").value(6));
        send(put("/api/analytics/dashboard-preferences"), exec, "{\"widgets\":[\"revenue\",\"openPipeline\"],\"period\":\"quarter\"}")
                .andExpect(status().isOk());
        send(get("/api/analytics/dashboard-preferences"), exec, null)
                .andExpect(jsonPath("$.data.widgets").value(contains("revenue", "openPipeline")))
                .andExpect(jsonPath("$.data.period").value("quarter"));
        send(put("/api/analytics/dashboard-preferences"), exec, "{\"widgets\":[\"bogus\"],\"period\":\"month\"}").andExpect(status().isBadRequest());
        send(put("/api/analytics/dashboard-preferences"), exec, "{\"widgets\":[\"revenue\"],\"period\":\"decade\"}").andExpect(status().isBadRequest());
        send(put("/api/analytics/dashboard-preferences"), exec, "{\"widgets\":[],\"period\":\"month\"}").andExpect(status().isBadRequest());
    }

    @Test
    void salesCustomerAndRevenueReports() throws Exception {
        // FR-9.2: opportunities created in the period, by stage and owner (the lead conversion opened a sixth, empty one).
        send(get("/api/analytics/sales").param("from", today).param("to", today), adminToken, null)
                .andExpect(jsonPath("$.data.totalCount").value(6))
                .andExpect(jsonPath("$.data.totalValue").value(9500.0))
                .andExpect(jsonPath("$.data.byStage[?(@.key == 'won')].count").value(contains(4)))
                .andExpect(jsonPath("$.data.byStage[?(@.key == 'won')].label").value(contains("Won")))
                .andExpect(jsonPath("$.data.byOwner[?(@.ownerId == " + execA.getId() + ")].wonCount").value(contains(2)))
                .andExpect(jsonPath("$.data.byOwner[?(@.ownerId == " + execA.getId() + ")].openValue").value(contains(2000.0)));

        // FR-9.3: counts, industry segments, activity (the converted lead became a fourth, trading customer).
        send(get("/api/analytics/customers").param("from", today).param("to", today), tenant.token(MARKETING_EXECUTIVE), null)
                .andExpect(jsonPath("$.data.activeCustomers").value(4))
                .andExpect(jsonPath("$.data.newCustomers").value(4))
                .andExpect(jsonPath("$.data.contactedCustomers").value(1))
                .andExpect(jsonPath("$.data.notContactedCustomers").value(3))
                .andExpect(jsonPath("$.data.byIndustry[?(@.key == 'trading')].count").value(contains(3)))
                .andExpect(jsonPath("$.data.byIndustry[?(@.key == 'trading')].label").value(contains("Trading")))
                .andExpect(jsonPath("$.data.activityByChannel[0].count").value(1));

        // FR-9.4: revenue by period, product, industry and salesperson.
        String finance = tenant.token(FINANCE_APPROVER);
        send(get("/api/analytics/revenue").param("from", yesterday).param("to", today), finance, null)
                .andExpect(jsonPath("$.data.totalRevenue").value(7500.0))
                .andExpect(jsonPath("$.data.wonDeals").value(4))
                .andExpect(jsonPath("$.data.byProduct[?(@.key == 'Kit')].value").value(contains(6500.0)))
                .andExpect(jsonPath("$.data.byProduct[?(@.key == '(not specified)')].value").value(contains(1000.0)))
                .andExpect(jsonPath("$.data.byIndustry[?(@.key == 'software')].value").value(contains(1000.0)))
                .andExpect(jsonPath("$.data.bySalesperson[0].key").value(String.valueOf(execA.getId())))
                .andExpect(jsonPath("$.data.bySalesperson[0].value").value(6000.0));
        // FR-9.6 industry filter.
        send(get("/api/analytics/revenue").param("from", today).param("to", today).param("industry", "trading"), finance, null)
                .andExpect(jsonPath("$.data.totalRevenue").value(4500.0));

        // Role permissions on the reports.
        send(get("/api/analytics/revenue"), managerToken, null).andExpect(status().isForbidden());
        send(get("/api/analytics/sales"), tenant.token(SALES_EXECUTIVE), null).andExpect(status().isForbidden());
        send(get("/api/analytics/sales"), finance, null).andExpect(status().isForbidden());
        send(get("/api/analytics/customers"), tenant.token(SERVICE_AGENT), null).andExpect(status().isForbidden());
        send(get("/api/analytics/sales").param("from", today).param("to", yesterday), adminToken, null).andExpect(status().isBadRequest());
    }

    @Test
    void teamPerformanceAndFiltersStayWithinScope() throws Exception {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        send(post("/api/sales-targets"), managerToken, "{\"userId\":" + execA.getId() + ",\"metric\":\"revenue\",\"targetValue\":10000,\"periodStart\":\""
                + monthStart + "\",\"periodEnd\":\"" + monthStart.plusMonths(1).minusDays(1) + "\"}").andExpect(status().isOk());
        send(post("/api/commission-rules"), adminToken, "{\"name\":\"All\",\"priority\":1,\"ratePercent\":10}").andExpect(status().isOk());
        long customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Late\",\"industry\":\"trading\",\"ownerId\":" + execB.getId() + "}"));
        won(customer, execB, 3000, null); // commission is earned on deals won after a rule exists

        // FR-9.5: the manager sees their team only — revenue, attainment, activities, commission.
        send(get("/api/analytics/team-performance").param("from", today).param("to", today), managerToken, null)
                .andExpect(jsonPath("$.data.people[*].userId").value(containsInAnyOrder(execA.getId().intValue(), execB.getId().intValue(),
                        tenant.user(SALES_MANAGER).getId().intValue())))
                .andExpect(jsonPath("$.data.people[?(@.userId == " + execA.getId() + ")].revenue").value(contains(4000.0)))
                .andExpect(jsonPath("$.data.people[?(@.userId == " + execA.getId() + ")].targetValue").value(contains(10000.0)))
                .andExpect(jsonPath("$.data.people[?(@.userId == " + execA.getId() + ")].calls").value(contains(1)))
                .andExpect(jsonPath("$.data.people[?(@.userId == " + execB.getId() + ")].commission").value(contains(300.0)))
                .andExpect(jsonPath("$.data.people[?(@.userId == " + execB.getId() + ")].teamName").value(contains("West")))
                .andExpect(jsonPath("$.data.teams[0].name").value("West"))
                .andExpect(jsonPath("$.data.teams[0].revenue").value(8000.0))
                .andExpect(jsonPath("$.data.teams[0].commission").value(300.0));

        // FR-9.6: team and user filters narrow; outside the caller's scope they are refused.
        send(get("/api/analytics/sales").param("from", today).param("to", today).param("userId", String.valueOf(execB.getId())), managerToken, null)
                .andExpect(jsonPath("$.data.totalValue").value(4000.0));
        send(get("/api/analytics/sales").param("from", today).param("to", today).param("teamId", String.valueOf(teamId)), adminToken, null)
                .andExpect(jsonPath("$.data.totalValue").value(12000.0));
        send(get("/api/analytics/sales").param("userId", String.valueOf(outsider.getId())), managerToken, null).andExpect(status().isForbidden());
        String exec = tenant.token(SALES_EXECUTIVE);
        send(get("/api/analytics/headline").param("userId", String.valueOf(execB.getId())), exec, null).andExpect(status().isForbidden());
        send(get("/api/analytics/headline").param("from", today).param("to", today).param("teamId", String.valueOf(teamId)), exec, null)
                .andExpect(jsonPath("$.data.current.revenue").value(4000.0)); // own team, but still only their own records

        // Filter choices follow the same scope.
        send(get("/api/analytics/filter-options"), managerToken, null)
                .andExpect(jsonPath("$.data.teams[*].id").value(contains((int) teamId)))
                .andExpect(jsonPath("$.data.users.length()").value(3))
                .andExpect(jsonPath("$.data.industries[?(@.code == 'trading')]").exists());
        send(get("/api/analytics/filter-options"), exec, null).andExpect(jsonPath("$.data.users.length()").value(1));
    }

    private long lead(User owner, String industry) throws Exception {
        return idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Lead " + System.nanoTime() + "\",\"companyName\":\"Prospect\",\"industry\":\""
                + industry + "\",\"source\":\"website\",\"ownerId\":" + owner.getId() + "}"));
    }

    private long won(long customer, User owner, int value, String product) throws Exception {
        long id = idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customer + ",\"dealValue\":" + value + ",\"ownerId\":" + owner.getId()
                + (product != null ? ",\"productService\":\"" + product + "\"" : "") + "}"));
        send(patch("/api/opportunities/" + id + "/stage"), adminToken, "{\"stage\":\"won\",\"force\":true}").andExpect(status().isOk());
        return id;
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
