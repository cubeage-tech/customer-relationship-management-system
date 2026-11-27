package com.company.crm.performance;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.company.crm.common.enums.RoleType.ADMIN;
import static com.company.crm.common.enums.RoleType.SALES_EXECUTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NFR-7.1b: search and filter answer within 2 s, and NFR-7.1c: the statistical AI features within 2 s,
 * at the agreed scale for one tenant — 50,000 leads, 30,000 customers, 30,000 deals, 10,000 quotations
 * (20,000 line items) and 30,000 campaign recipients. Each call is made once to warm up, then timed.
 * (The generative features' 8 s budget is enforced by the gateway's timeout; see ClaudeGatewayTest.)
 */
class ScalePerformanceTest extends PostgresIntegrationTest {

    private static final long BUDGET_MS = 2_000;

    @Autowired private JdbcTemplate jdbc;

    @Test
    void searchFiltersAndStatisticalAiAnswerWithinTwoSecondsAtScale() throws Exception {
        TestTenant tenant = testTenants.tenantWithAllRoles("Scale Co " + System.nanoTime());
        long t = tenant.tenant().getId();
        long admin = tenant.user(ADMIN).getId();
        long exec = tenant.user(SALES_EXECUTIVE).getId();
        String adminToken = tenant.token(ADMIN);
        try {
            seed(t, admin, exec);
            Long customer = jdbc.queryForObject("SELECT id FROM customers WHERE tenant_id = ? ORDER BY id LIMIT 1", Long.class, t);
            Long segment = jdbc.queryForObject("SELECT id FROM customer_segments WHERE tenant_id = ? ORDER BY id LIMIT 1", Long.class, t);
            String products = "{\"customerId\":" + customer + ",\"productNames\":[" + String.join(",",
                    java.util.stream.IntStream.range(0, 20).mapToObj(i -> "\"Product " + i + "\"").toList()) + "]}";

            Map<String, MockHttpServletRequestBuilder> calls = new LinkedHashMap<>();
            // NFR-7.1b
            calls.put("leads: first page", get("/api/leads").param("page", "0").param("size", "50"));
            calls.put("leads: search", get("/api/leads").param("search", "Perf Co 4999").param("page", "0").param("size", "50"));
            calls.put("leads: stage + source", get("/api/leads").param("stage", "contacted").param("source", "referral").param("page", "0").param("size", "50"));
            calls.put("leads: narrow search, unpaged", get("/api/leads").param("search", "Lead 12345"));
            calls.put("customers: search", get("/api/customers").param("search", "Perf Customer 2999").param("page", "0").param("size", "50"));
            calls.put("customers: industry + status", get("/api/customers").param("industry", "healthcare").param("status", "active").param("page", "0").param("size", "50"));
            calls.put("deals: stage", get("/api/opportunities").param("stage", "proposal").param("page", "0").param("size", "50"));
            calls.put("deals: search", get("/api/opportunities").param("search", "Perf Customer 1777").param("page", "0").param("size", "50"));
            // NFR-7.1c (statistical)
            calls.put("AI: price recommendations (20 products)", post("/api/ai/price-recommendations").contentType(MediaType.APPLICATION_JSON).content(products));
            calls.put("AI: segment recommendations", get("/api/campaigns/segment-recommendations").param("channel", "email"));
            calls.put("AI: campaign prediction", get("/api/campaigns/prediction").param("channel", "email").param("segmentId", String.valueOf(segment)));
            calls.put("AI: offer recommendations", get("/api/customers/" + customer + "/offer-recommendations"));
            calls.put("AI: lead scores (all 50,000)", get("/api/ai/lead-score").param("minScore", "70"));
            calls.put("AI: forecast", get("/api/ai/forecast"));
            calls.put("AI: deal probabilities (all open deals)", get("/api/ai/deal-probabilities"));

            Map<String, Long> timings = new LinkedHashMap<>();
            for (Map.Entry<String, MockHttpServletRequestBuilder> call : calls.entrySet()) {
                timings.put(call.getKey(), time(call.getValue(), adminToken));
            }
            timings.put("leads: first page, own scope (sales executive)",
                    time(get("/api/leads").param("page", "0").param("size", "50"), tenant.token(SALES_EXECUTIVE)));

            List<String> slow = new ArrayList<>();
            System.out.println("NFR-7.1b/7.1c timings at scale (ms):");
            timings.forEach((name, ms) -> {
                System.out.printf("  %-52s %6d%n", name, ms);
                if (ms > BUDGET_MS) {
                    slow.add(name + " took " + ms + " ms");
                }
            });
            assertThat(slow).as("calls over the %d ms budget", BUDGET_MS).isEmpty();
        } finally {
            cleanUp(t);
        }
    }

    /** Warm-up once, then the timed call. */
    private long time(MockHttpServletRequestBuilder request, String token) throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        long start = System.nanoTime();
        mockMvc.perform(request.header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        return (System.nanoTime() - start) / 1_000_000;
    }

    private void seed(long t, long admin, long exec) {
        jdbc.update("""
                INSERT INTO customers (tenant_id, company_name, industry, status, owner_id, email_hash, created_at)
                SELECT ?, 'Perf Customer ' || g, (ARRAY['trading','manufacturing','healthcare'])[1 + g % 3], 'active',
                       CASE WHEN g % 2 = 0 THEN ? ELSE ? END, md5(g::text), now() - make_interval(mins => g)
                FROM generate_series(1, 30000) g""", t, admin, exec);
        jdbc.update("""
                INSERT INTO leads (tenant_id, lead_name, company_name, industry, source, stage, owner_id, created_at)
                SELECT ?, 'Lead ' || g, 'Perf Co ' || g, (ARRAY['trading','manufacturing','healthcare'])[1 + g % 3],
                       (ARRAY['website','referral','cold_call','exhibition'])[1 + g % 4],
                       (ARRAY['new_lead','contacted','meeting','quotation','negotiation','converted'])[1 + g % 6],
                       CASE WHEN g % 2 = 0 THEN ? ELSE ? END, now() - make_interval(mins => g)
                FROM generate_series(1, 50000) g""", t, admin, exec);
        jdbc.update("""
                INSERT INTO opportunities (tenant_id, customer_id, stage, deal_value, owner_id, created_at)
                SELECT c.tenant_id, c.id, (ARRAY['qualification','proposal','negotiation','won','lost'])[1 + (c.id % 5)::int],
                       1000 + c.id % 9000, c.owner_id, c.created_at
                FROM customers c WHERE c.tenant_id = ?""", t);
        jdbc.update("""
                INSERT INTO quotations (tenant_id, customer_id, quotation_number, status, owner_id, created_at)
                SELECT c.tenant_id, c.id, 'PERF-' || c.id, (ARRAY['approved','rejected','expired','pending'])[1 + (c.id % 4)::int],
                       c.owner_id, now() - make_interval(days => (c.id % 300)::int)
                FROM (SELECT * FROM customers WHERE tenant_id = ? ORDER BY id LIMIT 10000) c""", t);
        jdbc.update("""
                INSERT INTO quotation_line_items (quotation_id, product_name, quantity, unit_price, discount_percent)
                SELECT q.id, 'Product ' || ((q.id + k) % 20), 1, 1000, ((q.id + k) % 5) * 5
                FROM quotations q CROSS JOIN generate_series(0, 1) k WHERE q.tenant_id = ?""", t);
        jdbc.update("INSERT INTO products (tenant_id, name, unit_price) SELECT ?, 'Product ' || g, 1000 FROM generate_series(0, 19) g", t);
        jdbc.update("""
                INSERT INTO customer_segments (tenant_id, name, industries)
                VALUES (?, 'Perf traders', 'trading'), (?, 'Perf makers', 'manufacturing'), (?, 'Perf clinics', 'healthcare')""", t, t, t);
        Long campaign = jdbc.queryForObject("INSERT INTO campaigns (tenant_id, name, channel, status, launched_at) "
                + "VALUES (?, 'Perf mailer', 'email', 'completed', now() - interval '20 days') RETURNING id", Long.class, t);
        jdbc.update("""
                INSERT INTO campaign_recipients (tenant_id, campaign_id, customer_id, status, tracking_token, sent_at, opened_at, clicked_at)
                SELECT c.tenant_id, ?, c.id, 'sent', md5('perf' || c.id), now() - interval '20 days',
                       CASE WHEN c.id % 3 = 0 THEN now() - interval '19 days' END, CASE WHEN c.id % 10 = 0 THEN now() - interval '19 days' END
                FROM customers c WHERE c.tenant_id = ?""", campaign, t);
        jdbc.update("""
                INSERT INTO promotions (tenant_id, code, name, discount_type, discount_value)
                SELECT ?, 'PERF' || g, 'Perf offer ' || g, CASE WHEN g % 2 = 0 THEN 'percent' ELSE 'amount' END, 5 + g FROM generate_series(1, 10) g""", t);
        jdbc.execute("ANALYZE customers; ANALYZE leads; ANALYZE opportunities; ANALYZE quotations; ANALYZE quotation_line_items; ANALYZE campaign_recipients");
    }

    private void cleanUp(long t) {
        jdbc.update("DELETE FROM promotions WHERE tenant_id = ?", t);
        jdbc.update("DELETE FROM campaign_recipients WHERE tenant_id = ?", t);
        jdbc.update("DELETE FROM campaigns WHERE tenant_id = ?", t);
        jdbc.update("DELETE FROM customer_segments WHERE tenant_id = ?", t);
        jdbc.update("DELETE FROM products WHERE tenant_id = ?", t);
        jdbc.update("DELETE FROM quotation_line_items WHERE quotation_id IN (SELECT id FROM quotations WHERE tenant_id = ?)", t);
        jdbc.update("DELETE FROM quotations WHERE tenant_id = ?", t);
        jdbc.update("DELETE FROM opportunities WHERE tenant_id = ?", t);
        jdbc.update("DELETE FROM leads WHERE tenant_id = ?", t);
        jdbc.update("DELETE FROM customers WHERE tenant_id = ?", t);
    }
}
