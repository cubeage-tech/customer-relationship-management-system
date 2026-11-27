package com.company.crm.ai;

import com.company.crm.ai.scheduler.LeadScoreRefreshJob;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-AI-1.1 (lead score with budget and buying behaviour), FR-AI-1.2 (score and label kept on the
 * lead), FR-AI-1.3 (filter and sort by score), FR-AI-2.1 (forecast from history and pipeline),
 * FR-AI-2.2 (close probability per open deal), FR-AI-2.3 (forecast vs target) and FR-AI-3.1
 * (product recommendations).
 */
class SalesIntelligenceIntegrationTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private LeadScoreRefreshJob refreshJob;

    private TestTenant tenant;
    private String adminToken;
    private long tenantId;
    private long adminId;

    @BeforeEach
    void setUp() {
        tenant = testTenants.tenantWithAllRoles("Intel Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        tenantId = tenant.tenant().getId();
        adminId = tenant.user(ADMIN).getId();
    }

    // ==================== FR-AI-1.1 / 1.2 / 1.3 ====================

    @Test
    void leadScoresUseBudgetAndBuyingAndAreKeptForSortingAndFiltering() throws Exception {
        long buyer = customer("Repeat Buyer");
        wonDeal(buyer, 50000, 40, 30, null);
        wonDeal(buyer, 50000, 20, 10, null);

        long hot = idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Asha\",\"companyName\":\"Repeat Buyer\",\"industry\":\"trading\","
                + "\"source\":\"referral\",\"budget\":60000}").andExpect(jsonPath("$.data.budget").value(60000)));
        long cold = idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Ben\",\"companyName\":\"Unknown Co\",\"industry\":\"software\","
                + "\"source\":\"exhibition\"}"));
        send(patch("/api/leads/" + hot + "/stage"), adminToken, "{\"stage\":\"negotiation\",\"force\":true}").andExpect(status().isOk());

        // FR-AI-1.2: the score and label are on the lead right after it changes.
        send(get("/api/leads/" + hot), adminToken, null)
                .andExpect(jsonPath("$.data.aiScore").isNumber())
                .andExpect(jsonPath("$.data.aiScoreLabel").value(in(List.of("High", "Medium", "Low"))));
        send(get("/api/ai/lead-score").param("leadId", String.valueOf(hot)), adminToken, null)
                .andExpect(jsonPath("$.data[0].factors.length()").value(7))
                .andExpect(jsonPath("$.data[0].factors[?(@.name == 'budget')].value").value(contains(1.0)))
                .andExpect(jsonPath("$.data[0].factors[?(@.name == 'budget')].detail").value(contains(containsString("typical won deal of 50000"))))
                .andExpect(jsonPath("$.data[0].factors[?(@.name == 'buying')].detail").value(contains("this company has bought 2 time(s) before")));
        send(get("/api/ai/lead-score").param("leadId", String.valueOf(cold)), adminToken, null)
                .andExpect(jsonPath("$.data[0].factors[?(@.name == 'budget')].detail").value(contains("budget not recorded")))
                .andExpect(jsonPath("$.data[0].factors[?(@.name == 'buying')].detail").value(contains("no purchases by this company yet")));

        // The refresh job keeps every stored score current.
        jdbc.update("UPDATE leads SET ai_score = NULL, ai_score_label = NULL WHERE tenant_id = ?", tenantId);
        refreshJob.refresh();
        int hotScore = jdbc.queryForObject("SELECT ai_score FROM leads WHERE id = ?", Integer.class, hot);
        int coldScore = jdbc.queryForObject("SELECT ai_score FROM leads WHERE id = ?", Integer.class, cold);
        assertThat(hotScore).isGreaterThan(coldScore);

        // FR-AI-1.3: sort and filter by it.
        send(get("/api/leads").param("sort", "aiScore,desc").param("page", "0").param("size", "10"), adminToken, null)
                .andExpect(jsonPath("$.data.items[0].id").value(hot))
                .andExpect(jsonPath("$.data.items[1].id").value(cold));
        send(get("/api/leads").param("minScore", String.valueOf(hotScore)), adminToken, null)
                .andExpect(jsonPath("$.data[*].id", contains((int) hot)));
        String label = jdbc.queryForObject("SELECT ai_score_label FROM leads WHERE id = ?", String.class, cold);
        send(get("/api/leads").param("scoreLabel", label), adminToken, null)
                .andExpect(jsonPath("$.data[*].id", hasItem((int) cold)));
    }

    // ==================== FR-AI-2.2 ====================

    @Test
    void eachOpenDealGetsACloseProbabilityWithReasons() throws Exception {
        long repeat = customer("Loyal Ltd");
        wonDeal(repeat, 20000, 60, 30, null);
        long fresh = customer("Fresh Ltd");
        long overdue = openDeal(repeat, "proposal", 20000, LocalDate.now().minusDays(3));
        long early = openDeal(fresh, "qualification", 20000, LocalDate.now().plusDays(20));
        long closed = jdbc.queryForObject("SELECT id FROM opportunities WHERE customer_id = ? AND stage = 'won'", Long.class, repeat);

        String all = send(get("/api/ai/deal-probabilities"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        List<Double> chances = JsonPath.read(all, "$.data[*].probability");
        assertThat(chances).isSortedAccordingTo((a, b) -> Double.compare(b, a));

        send(get("/api/ai/deal-probabilities").param("opportunityId", String.valueOf(overdue)), adminToken, null)
                .andExpect(jsonPath("$.data[0].percent").value(both(greaterThanOrEqualTo(2)).and(lessThanOrEqualTo(95))))
                .andExpect(jsonPath("$.data[0].label").value(in(List.of("High", "Medium", "Low"))))
                .andExpect(jsonPath("$.data[0].factors[*].name", hasItems("stage", "overdue", "customer", "engagement")))
                .andExpect(jsonPath("$.data[0].factors[?(@.name == 'overdue')].effect").value(contains(0.7)))
                .andExpect(jsonPath("$.data[0].factors[?(@.name == 'customer')].detail").value(contains("repeat customer (1 won deal)")));
        send(get("/api/ai/deal-probabilities").param("opportunityId", String.valueOf(early)), adminToken, null)
                .andExpect(jsonPath("$.data[0].factors[*].name", not(hasItem("overdue"))));
        send(get("/api/ai/deal-probabilities").param("opportunityId", String.valueOf(closed)), adminToken, null)
                .andExpect(status().isBadRequest());
        send(get("/api/ai/deal-probabilities"), tenant.token(SERVICE_AGENT), null).andExpect(status().isForbidden());
    }

    // ==================== FR-AI-2.1 / 2.3 ====================

    @Test
    void theForecastAddsHistoryAndComparesWithTheTarget() throws Exception {
        long customer = customer("Forecast Co");
        // A deal created and won within days: the run rate of quick new business.
        wonDeal(customer, 18000, 10, 5, null);
        LocalDate nextMonth = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        openDeal(customer, "negotiation", 10000, nextMonth.plusDays(9));
        jdbc.update("INSERT INTO sales_targets (tenant_id, user_id, metric, target_value, period_start, period_end) VALUES (?, ?, 'revenue', 50000, ?, ?)",
                tenantId, adminId, Date.valueOf(nextMonth), Date.valueOf(nextMonth.with(TemporalAdjusters.lastDayOfMonth())));

        String body = send(get("/api/ai/forecast").param("period", "next_month"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history.length()").value(6))
                .andExpect(jsonPath("$.data.target").value(50000.0))
                .andReturn().getResponse().getContentAsString();
        double expected = ((Number) JsonPath.read(body, "$.data.expectedRevenue")).doubleValue();
        double pipeline = ((Number) JsonPath.read(body, "$.data.weightedPipeline")).doubleValue();
        double newBusiness = ((Number) JsonPath.read(body, "$.data.newBusiness")).doubleValue();
        int days = nextMonth.lengthOfMonth();
        assertThat(newBusiness).isCloseTo(18000.0 * days / 180, offset(1.0));
        assertThat(expected).isCloseTo(pipeline + newBusiness, offset(0.05));
        assertThat(((Number) JsonPath.read(body, "$.data.low")).doubleValue()).isLessThanOrEqualTo(expected);
        assertThat(((Number) JsonPath.read(body, "$.data.high")).doubleValue()).isGreaterThanOrEqualTo(expected);
        // FR-AI-2.3: variance from the target, and what is still missing.
        assertThat(((Number) JsonPath.read(body, "$.data.variancePercent")).doubleValue()).isCloseTo((expected - 50000) / 500, offset(0.1));
        assertThat(((Number) JsonPath.read(body, "$.data.gapToTarget")).doubleValue()).isCloseTo(50000 - expected, offset(0.05));
    }

    // ==================== FR-AI-3.1 ====================

    @Test
    void productsAreRecommendedFromPurchaseHistory() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO products (tenant_id, name, unit_price, active) VALUES (?, 'Valve', 250, true), (?, 'Seal kit', 90, true), "
                + "(?, 'Old gasket', 40, false)", tenantId, tenantId, tenantId);
        for (String name : List.of("Alpha", "Beta", "Gamma")) {
            long c = customer(name + " Plant");
            approved(c, now.minusDays(90), "Pump", "Valve", "Old gasket");
            approved(c, now.minusDays(50), "Seal kit"); // bought after the pump
        }
        long target = customer("Delta Plant");
        approved(target, now.minusDays(100), "Pump");
        approved(target, now.minusDays(70), "Filter");
        approved(target, now.minusDays(40), "Filter"); // every ~30 days, last 40 days ago: due

        send(get("/api/ai/product-recommendations").param("customerId", String.valueOf(target)), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchased", containsInAnyOrder("Pump", "Filter")))
                .andExpect(jsonPath("$.data.recommendations[?(@.productName == 'Valve')].kind").value(contains("bought_together")))
                .andExpect(jsonPath("$.data.recommendations[?(@.productName == 'Valve')].unitPrice").value(contains(250.0)))
                .andExpect(jsonPath("$.data.recommendations[?(@.productName == 'Valve')].reason").value(contains(containsString("100% of customers who bought Pump"))))
                .andExpect(jsonPath("$.data.recommendations[?(@.productName == 'Seal kit')].kind").value(contains("next_purchase")))
                .andExpect(jsonPath("$.data.recommendations[?(@.productName == 'Filter')].kind").value(contains("reorder")))
                .andExpect(jsonPath("$.data.recommendations[*].productName", not(hasItem("Old gasket")))) // inactive
                .andExpect(jsonPath("$.data.recommendations[*].productName", not(hasItem("Pump"))));      // already bought
        send(get("/api/ai/product-recommendations").param("customerId", String.valueOf(target)),
                testTenants.tenantWithAllRoles("Other " + System.nanoTime()).token(ADMIN), null).andExpect(status().isNotFound());
    }

    // ==================== helpers ====================

    private long customer(String name) throws Exception {
        return idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"" + name + "\",\"industry\":\"trading\"}"));
    }

    private void wonDeal(long customerId, int value, int createdDaysAgo, int wonDaysAgo, String product) {
        jdbc.update("INSERT INTO opportunities (tenant_id, customer_id, stage, deal_value, owner_id, product_service, created_at, stage_changed_at) "
                + "VALUES (?, ?, 'won', ?, ?, ?, ?, ?)", tenantId, customerId, value, adminId, product,
                Timestamp.valueOf(LocalDateTime.now().minusDays(createdDaysAgo)), Timestamp.valueOf(LocalDateTime.now().minusDays(wonDaysAgo)));
    }

    private long openDeal(long customerId, String stage, int value, LocalDate closing) {
        return jdbc.queryForObject("INSERT INTO opportunities (tenant_id, customer_id, stage, deal_value, owner_id, expected_closing_date) "
                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id", Long.class, tenantId, customerId, stage, value, adminId, Date.valueOf(closing));
    }

    private void approved(long customerId, LocalDateTime on, String... products) {
        Long quotation = jdbc.queryForObject("INSERT INTO quotations (tenant_id, customer_id, quotation_number, status, owner_id, created_at, "
                + "customer_approved_at) VALUES (?, ?, ?, 'approved', ?, ?, ?) RETURNING id", Long.class, tenantId, customerId,
                "Q-" + System.nanoTime(), adminId, Timestamp.valueOf(on), Timestamp.valueOf(on));
        for (String product : products) {
            jdbc.update("INSERT INTO quotation_line_items (quotation_id, product_name, quantity, unit_price) VALUES (?, ?, 1, 100)", quotation, product);
        }
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
