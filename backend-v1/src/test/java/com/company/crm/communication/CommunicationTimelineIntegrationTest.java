package com.company.crm.communication;

import com.company.crm.common.enums.RoleType;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunicationTimelineIntegrationTest extends PostgresIntegrationTest {

    @Test
    void customerTimelineAggregatesChannelsChronologicallyAndAppliesTenantAndOwnerScope() throws Exception {
        TestTenant tenant = testTenants.tenantWithAllRoles("Communication Co " + System.nanoTime());
        User owner = tenant.user(RoleType.SALES_EXECUTIVE);
        User outOfScopeOwner = testTenants.createUser(tenant.tenant(), RoleType.SALES_EXECUTIVE);
        String customerOwnerToken = tenant.token(RoleType.ADMIN);
        long customerId = createCustomer(customerOwnerToken, owner.getId(), "Owned customer");
        long outOfScopeCustomerId = createCustomer(customerOwnerToken, outOfScopeOwner.getId(), "Other customer");
        String ownerToken = tenant.token(RoleType.SALES_EXECUTIVE);
        String timelineUrl = "/api/customers/" + customerId + "/communications";

        log(ownerToken, timelineUrl, "email", "2026-01-01T09:00:00", "Email notes");
        log(ownerToken, timelineUrl, "whatsapp", "2026-01-02T09:00:00", "WhatsApp notes");
        log(ownerToken, timelineUrl, "sms", "2026-01-03T09:00:00", "SMS notes");
        log(ownerToken, timelineUrl, "call", "2026-01-04T09:00:00", "Call notes");

        String timeline = mockMvc.perform(get(timelineUrl).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        List<String> channels = JsonPath.read(timeline, "$.data[*].channel");
        List<String> content = JsonPath.read(timeline, "$.data[*].content");
        assertThat(channels)
                .containsExactly("call", "sms", "whatsapp", "email");
        assertThat(content)
                .containsExactly("Call notes", "SMS notes", "WhatsApp notes", "Email notes");
        assertThat(JsonPath.<String>read(timeline, "$.data[0].createdByName"))
                .isEqualTo(owner.getFullName());

        mockMvc.perform(get("/api/customers/" + outOfScopeCustomerId + "/communications")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());

        TestTenant otherTenant = testTenants.tenantWithAllRoles("Other Communication Co " + System.nanoTime());
        mockMvc.perform(get(timelineUrl).header("Authorization",
                        "Bearer " + otherTenant.token(RoleType.ADMIN)))
                .andExpect(status().isNotFound());
    }

    private long createCustomer(String token, Long ownerId, String companyName) throws Exception {
        String body = mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + companyName
                                + "\",\"industry\":\"trading\",\"ownerId\":" + ownerId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void log(String token, String url, String channel, String occurredAt, String content) throws Exception {
        MockHttpServletRequestBuilder request = post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"" + channel
                        + "\",\"direction\":\"outbound\",\"content\":\"" + content
                        + "\",\"occurredAt\":\"" + occurredAt + "\"}");
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value(channel));
    }
}
