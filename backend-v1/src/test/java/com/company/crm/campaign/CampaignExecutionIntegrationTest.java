package com.company.crm.campaign;

import com.company.crm.campaign.scheduler.CampaignJobs;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FR-7.2 segments, FR-7.1c sending, FR-7.4 open/click/conversion tracking, FR-7.1b scheduling,
 * FR-7.3 promotions and redemptions.
 */
class CampaignExecutionIntegrationTest extends PostgresIntegrationTest {

    @Autowired private CampaignJobs campaignJobs;
    @Autowired private JdbcTemplate jdbcTemplate;

    private TestTenant tenant;
    private String adminToken;
    private String marketing;
    private long acme;       // manufacturing, has email, bought 5000 recently
    private long bolt;       // trading, has email, contacted recently
    private long cogs;       // manufacturing, no email
    private String acmeEmail;

    @BeforeEach
    void setUp() throws Exception {
        long n = System.nanoTime();
        tenant = testTenants.tenantWithAllRoles("Marketing Co " + n);
        adminToken = tenant.token(ADMIN);
        marketing = tenant.token(MARKETING_EXECUTIVE);
        acmeEmail = "acme" + n + "@buyer.test";
        acme = customer("Acme", "manufacturing", acmeEmail);
        bolt = customer("Bolt", "trading", "bolt" + n + "@buyer.test");
        cogs = customer("Cogs", "manufacturing", null);
        long deal = idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + acme + ",\"dealValue\":5000}"));
        send(patch("/api/opportunities/" + deal + "/stage"), adminToken, "{\"stage\":\"won\",\"force\":true}").andExpect(status().isOk());
        send(post("/api/communications/calls"), adminToken, "{\"customerId\":" + bolt + ",\"direction\":\"outbound\",\"outcome\":\"connected\"}")
                .andExpect(status().isOk());
    }

    @Test
    void segmentsMatchIndustryPurchasesEngagementAndLeadSource() throws Exception {
        long manufacturing = idOf(send(post("/api/segments"), marketing, "{\"name\":\"Manufacturers\",\"industries\":[\"manufacturing\"]}")
                .andExpect(jsonPath("$.data.customerCount").value(2)));
        send(get("/api/segments/" + manufacturing + "/customers"), marketing, null)
                .andExpect(jsonPath("$.data[*].companyName").value(containsInAnyOrder("Acme", "Cogs")));
        send(post("/api/segments"), marketing, "{\"name\":\"manufacturers\"}").andExpect(status().isConflict());

        segmentCount("{\"name\":\"Recent buyers\",\"purchasedWithinDays\":30}", 1);
        segmentCount("{\"name\":\"Lapsed\",\"notPurchasedWithinDays\":30}", 2);
        segmentCount("{\"name\":\"Big spenders\",\"minPurchaseValue\":1000}", 1);
        segmentCount("{\"name\":\"Engaged\",\"contactedWithinDays\":7}", 1);
        segmentCount("{\"name\":\"Quiet\",\"notContactedWithinDays\":7}", 2);
        segmentCount("{\"name\":\"Quiet manufacturers who buy\",\"industries\":[\"manufacturing\"],\"notContactedWithinDays\":7,\"purchasedWithinDays\":30}", 1);

        // Lead source: customers converted from an exhibition lead.
        long lead = idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"Ravi\",\"companyName\":\"Expo Buyer\",\"industry\":\"trading\",\"source\":\"exhibition\"}"));
        send(patch("/api/leads/" + lead + "/stage"), adminToken, "{\"stage\":\"converted\",\"force\":true}").andExpect(status().isOk());
        segmentCount("{\"name\":\"From exhibitions\",\"leadSource\":\"exhibition\"}", 1);

        send(post("/api/segments"), marketing, "{\"name\":\"Bad\",\"purchasedWithinDays\":30,\"notPurchasedWithinDays\":10}").andExpect(status().isBadRequest());
        send(post("/api/segments"), tenant.token(SALES_EXECUTIVE), "{\"name\":\"X\"}").andExpect(status().isForbidden());
    }

    @Test
    void emailCampaignSendsTracksAndHonoursUnsubscribe() throws Exception {
        long segment = idOf(send(post("/api/segments"), marketing, "{\"name\":\"Manufacturers\",\"industries\":[\"manufacturing\"]}"));
        long campaign = idOf(send(post("/api/campaigns"), marketing, "{\"name\":\"Diwali offer\",\"channel\":\"email\",\"segmentId\":" + segment
                + ",\"messageSubject\":\"20% off presses\",\"messageBody\":\"Hello!\\nSee https://example.com/offer for details.\"}"));

        send(patch("/api/campaigns/" + campaign + "/launch"), marketing, null)
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.launchedAt").isNotEmpty());
        campaignJobs.run();

        verify(mailService).sendMarketingEmail(eq(acmeEmail), eq("20% off presses"), contains("Unsubscribe:"),
                contains("/api/public/campaigns/o/"), anyString(), contains("/api/public/campaigns/u/"));
        send(get("/api/campaigns/" + campaign + "/recipients"), marketing, null)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.customerName == 'Acme')].status").value(hasItem("sent")))
                .andExpect(jsonPath("$.data[?(@.customerName == 'Cogs')].errorMessage").value(hasItem("No email address on record")));
        send(get("/api/communications").param("customerId", String.valueOf(acme)), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.channel == 'email')].subject").value(hasItem("20% off presses")));

        // Opened (pixel), clicked (redirect to the message's own link only).
        String token = jdbcTemplate.queryForObject("SELECT tracking_token FROM campaign_recipients WHERE campaign_id = ? AND customer_id = ?",
                String.class, campaign, acme);
        mockMvc.perform(get("/api/public/campaigns/o/" + token)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_GIF));
        mockMvc.perform(get("/api/public/campaigns/c/" + token + "/0"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/offer"));
        mockMvc.perform(get("/api/public/campaigns/c/" + token + "/7")).andExpect(status().isNotFound());

        // Converted: Acme wins another deal after receiving it.
        long deal = idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + acme + ",\"dealValue\":800}"));
        send(patch("/api/opportunities/" + deal + "/stage"), adminToken, "{\"stage\":\"won\",\"force\":true}").andExpect(status().isOk());
        send(get("/api/campaigns/" + campaign + "/metrics"), tenant.token(EXECUTIVE_OWNER), null)
                .andExpect(jsonPath("$.data.recipients").value(2))
                .andExpect(jsonPath("$.data.sent").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.opened").value(1))
                .andExpect(jsonPath("$.data.clicked").value(1))
                .andExpect(jsonPath("$.data.converted").value(1))
                .andExpect(jsonPath("$.data.openRate").value(100.0))
                .andExpect(jsonPath("$.data.conversionRate").value(100.0));

        // Message and audience are fixed once sent.
        send(put("/api/campaigns/" + campaign), marketing, "{\"name\":\"Diwali offer\",\"channel\":\"email\",\"segmentId\":" + segment
                + ",\"messageSubject\":\"Changed\",\"messageBody\":\"Hello!\\nSee https://example.com/offer for details.\"}")
                .andExpect(status().isBadRequest());

        // Unsubscribe: confirmation page, then POST; later campaigns skip the customer.
        mockMvc.perform(get("/api/public/campaigns/u/" + token)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<form method=\"post\">")));
        mockMvc.perform(post("/api/public/campaigns/u/" + token)).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT marketing_opt_out FROM customers WHERE id = ?", Boolean.class, acme)).isTrue();
        long second = idOf(send(post("/api/campaigns"), marketing, "{\"name\":\"Follow-up\",\"channel\":\"email\",\"segmentId\":" + segment
                + ",\"messageSubject\":\"Again\",\"messageBody\":\"Hi again\"}"));
        send(patch("/api/campaigns/" + second + "/launch"), marketing, null).andExpect(status().isOk());
        send(get("/api/campaigns/" + second + "/recipients"), marketing, null)
                .andExpect(jsonPath("$.data[?(@.customerName == 'Acme')].errorMessage").value(hasItem("Unsubscribed from marketing")));
    }

    @Test
    void campaignsLaunchOnScheduleAndCompleteAfterTheirEndDate() throws Exception {
        jdbcTemplate.update("UPDATE customers SET phone = ? WHERE id = ?", "9811111111", bolt);
        long segment = idOf(send(post("/api/segments"), marketing, "{\"name\":\"Traders\",\"industries\":[\"trading\"]}"));
        long sms =idOf(send(post("/api/campaigns"), marketing, "{\"name\":\"SMS blast\",\"channel\":\"sms\",\"segmentId\":" + segment + "}"));
        String later = LocalDateTime.now().plusHours(1).withNano(0).toString();
        send(patch("/api/campaigns/" + sms + "/schedule"), marketing, "{\"scheduledAt\":\"" + later + "\"}").andExpect(status().isBadRequest()); // no message
        send(put("/api/campaigns/" + sms), marketing, "{\"name\":\"SMS blast\",\"channel\":\"sms\",\"segmentId\":" + segment + ",\"messageBody\":\"Sale today\"}")
                .andExpect(status().isOk());
        send(patch("/api/campaigns/" + sms + "/schedule"), marketing, "{\"scheduledAt\":\"" + LocalDateTime.now().minusHours(1).withNano(0) + "\"}")
                .andExpect(status().isBadRequest()); // in the past
        send(patch("/api/campaigns/" + sms + "/schedule"), marketing, "{\"scheduledAt\":\"" + later + "\"}")
                .andExpect(jsonPath("$.data.status").value("scheduled"))
                .andExpect(jsonPath("$.data.scheduledAt").isNotEmpty());
        send(patch("/api/campaigns/" + sms + "/status"), marketing, "{\"status\":\"scheduled\"}").andExpect(status().isBadRequest());

        campaignJobs.run(); // not due yet
        send(get("/api/campaigns/" + sms), marketing, null).andExpect(jsonPath("$.data.status").value("scheduled"));
        jdbcTemplate.update("UPDATE campaigns SET scheduled_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), sms);
        campaignJobs.run();
        send(get("/api/campaigns/" + sms), marketing, null).andExpect(jsonPath("$.data.status").value("active"));
        // SMS isn't configured in tests, so the send is recorded as failed — but it was attempted.
        send(get("/api/campaigns/" + sms + "/recipients"), marketing, null)
                .andExpect(jsonPath("$.data[0].customerName").value("Bolt"))
                .andExpect(jsonPath("$.data[0].status").value("failed"));

        // Ended campaigns complete; non-sendable channels just activate; email without a segment activates only.
        jdbcTemplate.update("UPDATE campaigns SET end_date = ? WHERE id = ?", LocalDate.now().minusDays(1), sms);
        campaignJobs.run();
        send(get("/api/campaigns/" + sms), marketing, null).andExpect(jsonPath("$.data.status").value("completed"));
        long event = idOf(send(post("/api/campaigns"), marketing, "{\"name\":\"Expo\",\"channel\":\"event\"}"));
        send(patch("/api/campaigns/" + event + "/launch"), marketing, null).andExpect(jsonPath("$.data.status").value("active"));
        long legacy = idOf(send(post("/api/campaigns"), marketing, "{\"name\":\"Old style\",\"channel\":\"email\"}"));
        send(patch("/api/campaigns/" + legacy + "/status"), marketing, "{\"status\":\"active\"}").andExpect(jsonPath("$.data.status").value("active"));
        send(get("/api/campaigns/" + legacy + "/recipients"), marketing, null).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void promotionsAreRedeemedOncePerCustomerWithinTheirLimits() throws Exception {
        long promo = idOf(send(post("/api/promotions"), marketing,
                "{\"code\":\"spring10\",\"name\":\"Spring 10%\",\"discountType\":\"percent\",\"discountValue\":10,\"maxRedemptions\":2}")
                .andExpect(jsonPath("$.data.code").value("SPRING10"))
                .andExpect(jsonPath("$.data.redeemable").value(true)));
        send(post("/api/promotions"), marketing, "{\"code\":\"SPRING10\",\"name\":\"Dup\",\"discountType\":\"amount\",\"discountValue\":5}")
                .andExpect(status().isConflict());
        send(post("/api/promotions"), marketing, "{\"code\":\"BIG\",\"name\":\"Too big\",\"discountType\":\"percent\",\"discountValue\":150}")
                .andExpect(status().isBadRequest());
        send(post("/api/promotions"), tenant.token(SALES_MANAGER), "{\"code\":\"X\",\"name\":\"X\",\"discountType\":\"amount\",\"discountValue\":5}")
                .andExpect(status().isForbidden());

        long quotation = idOf(send(post("/api/quotations"), adminToken, "{\"customerId\":" + acme
                + ",\"lineItems\":[{\"productName\":\"Press\",\"quantity\":1,\"unitPrice\":1000,\"discountPercent\":10}]}"));
        String salesManager = tenant.token(SALES_MANAGER);
        send(post("/api/promotions/" + promo + "/redemptions"), salesManager,
                "{\"customerId\":" + acme + ",\"quotationId\":" + quotation + ",\"discountAmount\":100}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotationId").value(quotation))
                .andExpect(jsonPath("$.data.recordedByName").isNotEmpty());
        send(post("/api/promotions/" + promo + "/redemptions"), salesManager, "{\"customerId\":" + acme + "}").andExpect(status().isConflict());
        send(post("/api/promotions/" + promo + "/redemptions"), salesManager, "{\"customerId\":" + bolt + ",\"quotationId\":" + quotation + "}")
                .andExpect(status().isBadRequest()); // someone else's quotation
        send(post("/api/promotions/" + promo + "/redemptions"), salesManager, "{\"customerId\":" + bolt + "}").andExpect(status().isOk());
        send(post("/api/promotions/" + promo + "/redemptions"), salesManager, "{\"customerId\":" + cogs + "}")
                .andExpect(status().isBadRequest()); // limit of 2 reached

        send(get("/api/promotions"), marketing, null)
                .andExpect(jsonPath("$.data[0].redemptions").value(2))
                .andExpect(jsonPath("$.data[0].redeemable").value(false));
        send(get("/api/customers/" + acme + "/promotions"), adminToken, null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].promotionCode").value("SPRING10"))
                .andExpect(jsonPath("$.data[0].discountAmount").value(100));
        send(get("/api/promotions/" + promo + "/redemptions"), marketing, null).andExpect(jsonPath("$.data.length()").value(2));
        send(get("/api/promotions"), tenant.token(FINANCE_APPROVER), null).andExpect(status().isForbidden());

        long expired = idOf(send(post("/api/promotions"), marketing, "{\"code\":\"OLD\",\"name\":\"Old\",\"discountType\":\"amount\",\"discountValue\":50,"
                + "\"validFrom\":\"2020-01-01\",\"validUntil\":\"2020-12-31\"}"));
        send(post("/api/promotions/" + expired + "/redemptions"), salesManager, "{\"customerId\":" + cogs + "}").andExpect(status().isBadRequest());
    }

    private void segmentCount(String body, int expected) throws Exception {
        send(post("/api/segments"), marketing, body).andExpect(status().isOk()).andExpect(jsonPath("$.data.customerCount").value(expected));
    }

    private long customer(String name, String industry, String email) throws Exception {
        String emailPart = email != null ? ",\"email\":\"" + email + "\"" : "";
        return idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"" + name + "\",\"industry\":\"" + industry + "\"" + emailPart + "}"));
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
