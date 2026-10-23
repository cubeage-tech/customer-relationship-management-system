package com.company.crm.customer;

import com.company.crm.common.audit.AuditEvent;
import com.company.crm.common.audit.AuditEventRepository;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-1.6 configurable industry list for customers; AC-1.2 customer edits leave an audit trail. */
class CustomerIndustryAndAuditIntegrationTest extends PostgresIntegrationTest {

    @Autowired private AuditEventRepository auditEventRepository;

    private TestTenant tenant;
    private String adminToken;

    @BeforeEach
    void setUp() {
        tenant = testTenants.tenantWithAllRoles("Industry Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
    }

    @Test
    void industryListIsConfigurablePerTenant() throws Exception {
        // Every role reads the list; a new tenant starts with the SRS defaults.
        send(get("/api/industries"), tenant.token(FINANCE_APPROVER), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(11))
                .andExpect(jsonPath("$.data[0].code").value("manufacturing"));

        // Admin adds one; duplicates (any case) and non-admins are refused.
        send(post("/api/industries"), adminToken, "{\"label\":\"Food Processing\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("food_processing"))
                .andExpect(jsonPath("$.data.active").value(true));
        send(post("/api/industries"), adminToken, "{\"label\":\"food processing\"}").andExpect(status().isConflict());
        send(post("/api/industries"), tenant.token(SALES_MANAGER), "{\"label\":\"Mining\"}").andExpect(status().isForbidden());

        long customerId = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Agro Foods\",\"industry\":\"food_processing\"}"));
        send(post("/api/customers"), adminToken, "{\"companyName\":\"X\",\"industry\":\"space_mining\"}").andExpect(status().isBadRequest());

        // Rename keeps the code; customers follow automatically.
        send(put("/api/industries/food_processing"), adminToken, "{\"label\":\"Food & Beverage\"}")
                .andExpect(jsonPath("$.data.code").value("food_processing"))
                .andExpect(jsonPath("$.data.label").value("Food & Beverage"));

        // Deactivated: hidden from the picker and refused for new customers, but existing ones still save.
        send(put("/api/industries/food_processing"), adminToken, "{\"active\":false}").andExpect(status().isOk());
        send(get("/api/industries"), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.code == 'food_processing')]").isEmpty());
        send(get("/api/industries").param("includeInactive", "true"), adminToken, null)
                .andExpect(jsonPath("$.data[?(@.code == 'food_processing')].active").value(Matchers.contains(false)));
        send(post("/api/customers"), adminToken, "{\"companyName\":\"New Foods\",\"industry\":\"food_processing\"}").andExpect(status().isBadRequest());
        send(put("/api/customers/" + customerId), adminToken, "{\"companyName\":\"Agro Foods Ltd\",\"industry\":\"food_processing\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("food_processing"));

        // Another tenant has its own list — the custom code doesn't exist there.
        TestTenant other = testTenants.tenantWithAllRoles("Other Industry " + System.nanoTime());
        send(post("/api/customers"), other.token(ADMIN), "{\"companyName\":\"Y\",\"industry\":\"food_processing\"}").andExpect(status().isBadRequest());

        // Lead conversion still produces a customer with the lead's (standard) industry.
        long leadId = idOf(send(post("/api/leads"), adminToken,
                "{\"leadName\":\"Ravi\",\"companyName\":\"Conv Co\",\"industry\":\"healthcare\",\"source\":\"referral\"}"));
        String converted = send(patch("/api/leads/" + leadId + "/stage"), adminToken, "{\"stage\":\"converted\",\"force\":true}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long convertedCustomerId = ((Number) JsonPath.read(converted, "$.data.convertedCustomerId")).longValue();
        send(get("/api/customers/" + convertedCustomerId), adminToken, null).andExpect(jsonPath("$.data.industry").value("healthcare"));
    }

    @Test
    void customerChangesAreAudited() throws Exception {
        long customerId = idOf(send(post("/api/customers"), adminToken,
                "{\"companyName\":\"ABC Engineering\",\"industry\":\"automobile\",\"phone\":\"111\"}"));
        send(put("/api/customers/" + customerId), adminToken,
                "{\"companyName\":\"ABC Engineering\",\"industry\":\"automobile\",\"phone\":\"222\",\"notes\":\"Key account\"}").andExpect(status().isOk());
        // Saving without changes adds no entry.
        send(put("/api/customers/" + customerId), adminToken,
                "{\"companyName\":\"ABC Engineering\",\"industry\":\"automobile\",\"phone\":\"222\",\"notes\":\"Key account\"}").andExpect(status().isOk());
        send(post("/api/customers/" + customerId + "/contacts"), adminToken, "{\"name\":\"Priya\",\"designation\":\"Purchase Manager\"}")
                .andExpect(status().isOk());
        send(patch("/api/customers/" + customerId + "/archive"), adminToken, null).andExpect(status().isOk());
        send(patch("/api/customers/" + customerId + "/restore"), adminToken, null).andExpect(status().isOk());

        List<AuditEvent> events = auditEventRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
                tenant.tenant().getId(), "customer", customerId);
        assertThat(events).extracting(e -> e.getAction().name()).containsExactly(
                "CUSTOMER_CREATED", "CUSTOMER_UPDATED", "CUSTOMER_CONTACT_ADDED", "CUSTOMER_ARCHIVED", "CUSTOMER_RESTORED");
        assertThat(events).allSatisfy(e -> assertThat(e.getActorUserId()).isEqualTo(tenant.user(ADMIN).getId()));
        assertThat(events.get(1).getDetails()).isEqualTo("changed=phone,notes"); // field names only, no values
        assertThat(events.get(2).getDetails()).isEqualTo("contact=Priya");
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
