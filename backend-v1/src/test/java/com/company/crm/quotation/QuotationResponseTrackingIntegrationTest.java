package com.company.crm.quotation;

import com.company.crm.quotation.scheduler.QuotationExpiryJob;
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

import static com.company.crm.common.enums.RoleType.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** FR-4.5: customer response tracking — emailed view link marks Viewed; overdue offers expire. */
class QuotationResponseTrackingIntegrationTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private QuotationExpiryJob expiryJob;

    private TestTenant tenant;
    private String adminToken;
    private long customerId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Tracking Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        customerId = idOf(send(post("/api/customers"), adminToken,
                "{\"companyName\":\"ABC Engineering\",\"industry\":\"trading\",\"email\":\"buyer@abc.test\"}"));
    }

    @Test
    void sendingEmailsATrackedLinkThatMarksTheQuotationViewed() throws Exception {
        long id = createQuotation(customerId, LocalDate.now().plusDays(30));
        String body = send(patch("/api/quotations/" + id + "/send"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.viewedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String viewUrl = JsonPath.read(body, "$.data.viewUrl");
        assertThat(viewUrl).contains("/q/");
        String token = viewUrl.substring(viewUrl.lastIndexOf('/') + 1);
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);

        // The customer is emailed the link, and the email is on their timeline.
        verify(mailService).sendCustomerEmail(eq("buyer@abc.test"), contains("Quotation"), contains(viewUrl), anyString());
        send(get("/api/communications").param("customerId", String.valueOf(customerId)), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.channel == 'email')].status").value(hasItem("sent")));

        // A signed-in CRM user previewing the link doesn't count as the customer viewing it.
        send(get("/api/public/quotations/" + token), adminToken, null).andExpect(jsonPath("$.data.status").value("pending"));
        send(get("/api/quotations/" + id), adminToken, null).andExpect(jsonPath("$.data.viewedAt").doesNotExist());

        // The customer opens the link (no login): it becomes Viewed and shows no internal fields.
        mockMvc.perform(get("/api/public/quotations/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("viewed"))
                .andExpect(jsonPath("$.data.customerName").value("ABC Engineering"))
                .andExpect(jsonPath("$.data.lineItems.length()").value(1))
                .andExpect(jsonPath("$.data.ownerName").doesNotExist())
                .andExpect(jsonPath("$.data.notes").doesNotExist());
        send(get("/api/quotations/" + id), adminToken, null)
                .andExpect(jsonPath("$.data.status").value("viewed"))
                .andExpect(jsonPath("$.data.viewedAt").isNotEmpty());
        mockMvc.perform(get("/api/public/quotations/" + token + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // A later staff-recorded answer is not overwritten by another open.
        send(patch("/api/quotations/" + id + "/customer-status"), adminToken, "{\"status\":\"approved\"}").andExpect(status().isOk());
        mockMvc.perform(get("/api/public/quotations/" + token)).andExpect(jsonPath("$.data.status").value("approved"));

        mockMvc.perform(get("/api/public/quotations/not-a-real-token")).andExpect(status().isNotFound());
    }

    @Test
    void overdueQuotationsExpire() throws Exception {
        // Can't send an offer that has already lapsed.
        long lapsed = createQuotation(customerId, LocalDate.now().minusDays(1));
        send(patch("/api/quotations/" + lapsed + "/send"), adminToken, null).andExpect(status().isBadRequest());

        long pending = sendQuotation(LocalDate.now().plusDays(10));
        long viewed = sendQuotation(LocalDate.now().plusDays(10));
        long stillValid = sendQuotation(LocalDate.now().plusDays(10));
        mockMvc.perform(get("/api/public/quotations/" + tokenOf(viewed))).andExpect(jsonPath("$.data.status").value("viewed"));
        jdbcTemplate.update("UPDATE quotations SET valid_until = ? WHERE id IN (?, ?)", LocalDate.now().minusDays(1), pending, viewed);

        expiryJob.expireOverdue();

        send(get("/api/quotations/" + pending), adminToken, null).andExpect(jsonPath("$.data.status").value("expired"));
        send(get("/api/quotations/" + viewed), adminToken, null).andExpect(jsonPath("$.data.status").value("expired"));
        send(get("/api/quotations/" + stillValid), adminToken, null).andExpect(jsonPath("$.data.status").value("pending"));
        send(get("/api/quotations/" + lapsed), adminToken, null).andExpect(jsonPath("$.data.status").value("draft"));

        // The link applies the same rule on open, even before the job runs.
        long lapsedSinceSend = sendQuotation(LocalDate.now().plusDays(10));
        jdbcTemplate.update("UPDATE quotations SET valid_until = ? WHERE id = ?", LocalDate.now().minusDays(1), lapsedSinceSend);
        mockMvc.perform(get("/api/public/quotations/" + tokenOf(lapsedSinceSend))).andExpect(jsonPath("$.data.status").value("expired"));
    }

    @Test
    void customerWithoutEmailStillGetsALinkAndTheFailureIsLogged() throws Exception {
        long noEmailCustomer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"No Mail Ltd\",\"industry\":\"trading\"}"));
        long id = createQuotation(noEmailCustomer, null);
        send(patch("/api/quotations/" + id + "/send"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewUrl").isNotEmpty());
        send(get("/api/communications").param("customerId", String.valueOf(noEmailCustomer)), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.channel == 'email')].status").value(hasItem("failed")))
                .andExpect(jsonPath("$.data[?(@.channel == 'email')].errorMessage").value(hasItem("No email address on record")));
    }

    private long sendQuotation(LocalDate validUntil) throws Exception {
        long id = createQuotation(customerId, validUntil);
        send(patch("/api/quotations/" + id + "/send"), adminToken, null).andExpect(status().isOk());
        return id;
    }

    private String tokenOf(long quotationId) throws Exception {
        String body = send(get("/api/quotations/" + quotationId), adminToken, null).andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.data.viewUrl");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private long createQuotation(long customer, LocalDate validUntil) throws Exception {
        String valid = validUntil != null ? ",\"validUntil\":\"" + validUntil + "\"" : "";
        return idOf(send(post("/api/quotations"), adminToken, "{\"customerId\":" + customer + valid
                + ",\"lineItems\":[{\"productName\":\"Crane\",\"quantity\":1,\"unitPrice\":1000,\"discountPercent\":0}]}"));
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
