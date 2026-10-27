package com.company.crm.ai;

import com.company.crm.ai.llm.LlmGateway;
import com.company.crm.ai.service.AiQuotationDraftService.DraftPlan;
import com.company.crm.ai.service.AiQuotationDraftService.PlannedLine;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.company.crm.common.enums.RoleType.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-4.6 (quotation draft uses historical pricing), FR-4.7 (price recommendation), FR-7.5 (segments
 * most likely to respond), FR-7.6 (campaign prediction, and predicted vs actual), FR-7.7 (offers per
 * customer) and NFR-7.1c (AI response times against their budgets).
 */
class RecommendationsIntegrationTest extends PostgresIntegrationTest {

    @MockitoBean private LlmGateway llm;
    @Autowired private JdbcTemplate jdbc;

    private TestTenant tenant;
    private String adminToken;
    private long tenantId;
    private long adminId;

    @BeforeEach
    void setUp() {
        tenant = testTenants.tenantWithAllRoles("Insight Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        tenantId = tenant.tenant().getId();
        adminId = tenant.user(ADMIN).getId();
        when(llm.isConfigured()).thenReturn(true);
    }

    // ==================== FR-4.7 / FR-4.6 ====================

    @Test
    void pricesAreRecommendedFromQuotesWonAndLostAndFeedTheAiDraft() throws Exception {
        send(post("/api/products"), adminToken, "{\"name\":\"Crane\",\"unitPrice\":1000}").andExpect(status().isOk());
        long buyer = customer("Buyer Ltd", "trading", null);
        long other = customer("Other Ltd", "trading", null);
        // At no discount 2 of 6 were won, at 8% 5 of 6, at 25% 3 of 3: 8% earns the most (win rate × price).
        quotes(other, "Crane", 0, 2, 4);
        quotes(other, "Crane", 8, 5, 1);
        quotes(other, "Crane", 25, 3, 0);

        send(post("/api/ai/price-recommendations"), adminToken, "{\"customerId\":" + buyer + ",\"productNames\":[\"Crane\",\"Gizmo\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].recommendedDiscountPercent").value(8.0))
                .andExpect(jsonPath("$.data[0].recommendedNetPrice").value(920.0))
                .andExpect(jsonPath("$.data[0].decidedQuotes").value(15))
                .andExpect(jsonPath("$.data[0].wonQuotes").value(10))
                .andExpect(jsonPath("$.data[0].confidence").value("high"))
                .andExpect(jsonPath("$.data[0].levels.length()").value(3))
                .andExpect(jsonPath("$.data[0].explanation").value(containsString("about 8% off")))
                .andExpect(jsonPath("$.data[1].confidence").value("none"))
                .andExpect(jsonPath("$.data[1].recommendedDiscountPercent").value(0));

        // FR-4.6: the draft is made with that history, and each line says what it was based on.
        when(llm.structured(anyString(), anyString(), eq(DraftPlan.class)))
                .thenReturn(Optional.of(new DraftPlan(List.of(new PlannedLine("Crane", 2, 8, "Lifting")), "Here is our offer.")));
        send(post("/api/ai/quotation-draft"), adminToken, "{\"customerId\":" + buyer + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineItems[0].discountPercent").value(8.0))
                .andExpect(jsonPath("$.data.lineItems[0].pricingNote").value(containsString("won")));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), prompt.capture(), eq(DraftPlan.class));
        assertThat(prompt.getValue()).contains("Crane | 1000.00 | 8% (won 10 of 15)");

        // The customer's own accepted quote counts more, and is mentioned.
        quotes(buyer, "Crane", 8, 1, 0);
        send(post("/api/ai/price-recommendations"), adminToken, "{\"customerId\":" + buyer + ",\"productNames\":[\"Crane\"]}")
                .andExpect(jsonPath("$.data[0].customerQuotes").value(1))
                .andExpect(jsonPath("$.data[0].explanation").value(containsString("This customer accepted 1 of 1")));
        // Another tenant's admin can't ask about this customer.
        send(post("/api/ai/price-recommendations"), testTenants.tenantWithAllRoles("Other " + System.nanoTime()).token(ADMIN),
                "{\"customerId\":" + buyer + ",\"productNames\":[\"Crane\"]}").andExpect(status().isNotFound());
    }

    // ==================== FR-7.5 / FR-7.6 ====================

    @Test
    void segmentsAreRankedByLikelyResponseAndCampaignsPredictedBeforeLaunch() throws Exception {
        List<Long> traders = new ArrayList<>();
        List<Long> makers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            traders.add(customer("Trader " + i, "trading", "t" + i + "@shop.test"));
            makers.add(customer("Maker " + i, "manufacturing", "m" + i + "@works.test"));
        }
        long unsubscribed = customer("Trader opted out", "trading", "out@shop.test");
        jdbc.update("UPDATE customers SET marketing_opt_out = true WHERE id = ?", unsubscribed);
        long tradersSegment = segment("Traders", "trading");
        long makersSegment = segment("Makers", "manufacturing");

        // A past email campaign: traders opened 16 of 20, makers 2 of 20.
        long past = campaign("Spring mailer", "email", "completed", null);
        for (int i = 0; i < 20; i++) {
            recipient(past, traders.get(i), i < 16);
            recipient(past, makers.get(i), i < 2);
        }

        send(get("/api/campaigns/segment-recommendations").param("channel", "email"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].segmentId").value(tradersSegment))
                .andExpect(jsonPath("$.data[0].reachable").value(20))
                .andExpect(jsonPath("$.data[0].lift").value(greaterThan(1.2)))
                .andExpect(jsonPath("$.data[0].responseMeasure").value("open"))
                .andExpect(jsonPath("$.data[0].reason").value(containsString("more likely to open")))
                .andExpect(jsonPath("$.data[1].segmentId").value(makersSegment))
                .andExpect(jsonPath("$.data[1].lift").value(lessThan(0.8)));

        String prediction = send(get("/api/campaigns/prediction").param("channel", "email").param("segmentId", String.valueOf(tradersSegment))
                .param("budget", "1200"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members").value(21))
                .andExpect(jsonPath("$.data.reachable").value(20))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.basis").value("history"))
                .andExpect(jsonPath("$.data.notes[*]", hasItem(containsString("can't be reached"))))
                .andReturn().getResponse().getContentAsString();
        int expected = JsonPath.read(prediction, "$.data.opened.expected");
        int low = JsonPath.read(prediction, "$.data.opened.low");
        int high = JsonPath.read(prediction, "$.data.opened.high");
        assertThat(expected).isBetween(10, 16);
        assertThat(low).isLessThanOrEqualTo(expected);
        assertThat(high).isGreaterThanOrEqualTo(expected);
        // Makers are predicted to open far less.
        String makersPrediction = send(get("/api/campaigns/prediction").param("channel", "email")
                .param("segmentId", String.valueOf(makersSegment)), adminToken, null).andReturn().getResponse().getContentAsString();
        assertThat((int) JsonPath.read(makersPrediction, "$.data.opened.expected")).isLessThan(expected);

        // SMS can't track opens: only deliveries and conversions (and nobody here has a phone).
        send(get("/api/campaigns/prediction").param("channel", "sms").param("segmentId", String.valueOf(tradersSegment)), adminToken, null)
                .andExpect(jsonPath("$.data.opened").doesNotExist())
                .andExpect(jsonPath("$.data.reachable").value(0))
                .andExpect(jsonPath("$.data.notes[*]", hasItem(containsString("no open or click tracking"))));
        send(get("/api/campaigns/prediction").param("channel", "event").param("segmentId", String.valueOf(tradersSegment)), adminToken, null)
                .andExpect(status().isBadRequest());

        // A launched campaign: predicted (from the history before it) next to what actually happened.
        send(get("/api/campaigns/" + past + "/prediction"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actual.sent").value(40))
                .andExpect(jsonPath("$.data.actual.opened").value(18))
                .andExpect(jsonPath("$.data.basis").value("typical"));
    }

    // ==================== FR-7.7 ====================

    @Test
    void offersAreRankedForTheCustomerWithReasons() throws Exception {
        long buyer = customer("Corner Shop", "trading", "shop@corner.test");
        long peer = customer("Next Shop", "trading", "shop@next.test");
        long traders = segment("Traders", "trading");
        long forTraders = campaign("Traders offer", "email", "active", traders);
        long now = System.nanoTime();
        long made = promotion("TRADE" + now, "percent", 10, forTraders, null, true);
        promotion("FLAT" + now, "amount", 500, null, null, true);
        promotion("OLD" + now, "percent", 30, null, LocalDate.now().minusDays(1), true);
        long used = promotion("USED" + now, "percent", 15, null, null, true);
        promotion("OFF" + now, "percent", 40, null, null, false);
        redeem(used, buyer);
        redeem(made, peer);

        send(get("/api/customers/" + buyer + "/offer-recommendations"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.marketingOptOut").value(false))
                .andExpect(jsonPath("$.data.offers[0].code").value("TRADE" + now))
                .andExpect(jsonPath("$.data.offers[0].discountLabel").value("10% off"))
                .andExpect(jsonPath("$.data.offers[0].reasons", hasItem(containsString("Made for the segment \"Traders\""))))
                .andExpect(jsonPath("$.data.offers[0].reasons", hasItem(containsString("Taken by 1 other customer in the same industry"))))
                .andExpect(jsonPath("$.data.offers[*].code", not(hasItem("OLD" + now))))   // expired
                .andExpect(jsonPath("$.data.offers[*].code", not(hasItem("USED" + now))))  // already used
                .andExpect(jsonPath("$.data.offers[*].code", not(hasItem("OFF" + now))))   // inactive
                .andExpect(jsonPath("$.data.offers.length()").value(2));

        // NFR-7.1c: each statistical AI feature answered within its 2-second budget.
        send(get("/api/ai/performance"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.feature == 'offer_recommendation')].kind").value(contains("statistical")))
                .andExpect(jsonPath("$.data[?(@.feature == 'offer_recommendation')].budgetMs").value(contains(2000)))
                .andExpect(jsonPath("$.data[?(@.feature == 'offer_recommendation')].p95Ms", everyItem(lessThan(2000))));
    }

    // ==================== helpers ====================

    private long customer(String name, String industry, String email) throws Exception {
        return idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"" + name + "\",\"industry\":\"" + industry + "\""
                + (email != null ? ",\"email\":\"" + email + "\"" : "") + "}"));
    }

    /** {@code won} accepted and {@code lost} rejected quotes for the product at this discount. */
    private void quotes(long customerId, String product, double discount, int won, int lost) {
        for (int i = 0; i < won + lost; i++) {
            Long quotation = jdbc.queryForObject("INSERT INTO quotations (tenant_id, customer_id, quotation_number, status, owner_id) "
                    + "VALUES (?, ?, ?, ?, ?) RETURNING id", Long.class, tenantId, customerId, "Q-" + System.nanoTime(),
                    i < won ? "approved" : "rejected", adminId);
            jdbc.update("INSERT INTO quotation_line_items (quotation_id, product_name, quantity, unit_price, discount_percent) VALUES (?, ?, 1, 1000, ?)",
                    quotation, product, discount);
        }
    }

    private long segment(String name, String industries) {
        return jdbc.queryForObject("INSERT INTO customer_segments (tenant_id, name, industries) VALUES (?, ?, ?) RETURNING id", Long.class,
                tenantId, name, industries);
    }

    private long campaign(String name, String channel, String status, Long segmentId) {
        return jdbc.queryForObject("INSERT INTO campaigns (tenant_id, name, channel, status, segment_id, launched_at) VALUES (?, ?, ?, ?, ?, ?) "
                + "RETURNING id", Long.class, tenantId, name, channel, status, segmentId,
                "completed".equals(status) ? Timestamp.valueOf(LocalDateTime.now().minusDays(20)) : null);
    }

    private void recipient(long campaignId, long customerId, boolean opened) {
        Timestamp sent = Timestamp.valueOf(LocalDateTime.now().minusDays(20));
        jdbc.update("INSERT INTO campaign_recipients (tenant_id, campaign_id, customer_id, status, tracking_token, sent_at, opened_at) "
                + "VALUES (?, ?, ?, 'sent', ?, ?, ?)", tenantId, campaignId, customerId, "tok-" + System.nanoTime(), sent, opened ? sent : null);
    }

    private long promotion(String code, String type, double value, Long campaignId, LocalDate validUntil, boolean active) {
        return jdbc.queryForObject("INSERT INTO promotions (tenant_id, campaign_id, code, name, discount_type, discount_value, valid_until, active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class, tenantId, campaignId, code, code + " offer", type, value,
                validUntil == null ? null : java.sql.Date.valueOf(validUntil), active);
    }

    private void redeem(long promotionId, long customerId) {
        jdbc.update("INSERT INTO promotion_redemptions (tenant_id, promotion_id, customer_id) VALUES (?, ?, ?)", tenantId, promotionId, customerId);
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
