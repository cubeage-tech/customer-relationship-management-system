package com.company.crm.customfield;

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

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ARC-9 (industry-specific custom fields and templates) and NAV-M03 (lead source report). */
class CustomFieldsAndLeadSourcesIntegrationTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private TestTenant tenant;
    private String adminToken;
    private User exec;

    @BeforeEach
    void setUp() {
        tenant = testTenants.tenantWithAllRoles("Fields Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        exec = tenant.user(SALES_EXECUTIVE);
    }

    @Test
    void industryTemplateAddsFieldsOnlyForThatIndustry() throws Exception {
        send(get("/api/custom-fields/templates"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.industry == 'healthcare')].fields[*].key").value(hasItem("bed_count")));
        send(post("/api/custom-fields/templates/healthcare/apply"), adminToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(3));
        // applying again keeps what exists
        send(post("/api/custom-fields/templates/healthcare/apply"), adminToken, null)
                .andExpect(jsonPath("$.data.created").value(0)).andExpect(jsonPath("$.data.skipped").value(3));
        send(post("/api/custom-fields/templates/healthcare/apply"), tenant.token(SALES_MANAGER), null).andExpect(status().isForbidden());

        long hospital = customer("City Hospital", "healthcare");
        long shop = customer("Corner Shop", "trading");
        send(get("/api/custom-fields/values/customer/" + hospital), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("healthcare"))
                .andExpect(jsonPath("$.data.fields[*].key").value(containsInAnyOrder("facility_type", "bed_count")));
        send(get("/api/custom-fields/values/customer/" + shop), adminToken, null)
                .andExpect(jsonPath("$.data.fields.length()").value(0));

        send(put("/api/custom-fields/values/customer/" + hospital), tenant.token(SALES_EXECUTIVE),
                "{\"facility_type\":\"Hospital\",\"bed_count\":\"120\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.values.facility_type").value("Hospital"))
                .andExpect(jsonPath("$.data.values.bed_count").value(120));
        Integer audits = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'CUSTOM_FIELD_VALUES_UPDATED' AND entity_id = ?",
                Integer.class, hospital);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void valuesAreCheckedAgainstEachFieldAndRecordAccess() throws Exception {
        long requiredField = idOf(send(post("/api/custom-fields"), adminToken,
                "{\"entityType\":\"lead\",\"label\":\"Budget band\",\"fieldType\":\"select\",\"options\":[\"Low\",\"High\"],\"required\":true}"));
        send(post("/api/custom-fields"), adminToken, "{\"entityType\":\"lead\",\"label\":\"Visit date\",\"fieldType\":\"date\"}")
                .andExpect(jsonPath("$.data.key").value("visit_date"));
        send(post("/api/custom-fields"), adminToken, "{\"entityType\":\"lead\",\"label\":\"Choices\",\"fieldType\":\"select\"}")
                .andExpect(status().isBadRequest());
        long lead = lead("Ravi", "referral", exec);

        send(put("/api/custom-fields/values/lead/" + lead), adminToken, "{\"budget_band\":\"Medium\",\"visit_date\":\"soon\",\"unknown\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(allOf(containsString("unknown"), containsString("Budget band must be one of"),
                        containsString("Visit date must be a date"))));
        send(put("/api/custom-fields/values/lead/" + lead), adminToken, "{\"visit_date\":\"2026-11-02\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("Budget band is required")));
        send(put("/api/custom-fields/values/lead/" + lead), adminToken, "{\"budget_band\":\"High\",\"visit_date\":\"2026-11-02\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.values.visit_date").value("2026-11-02"));

        // switching a field off hides it but keeps its value
        send(put("/api/custom-fields/" + requiredField), adminToken,
                "{\"entityType\":\"lead\",\"label\":\"Budget band\",\"fieldType\":\"select\",\"options\":[\"Low\",\"High\"],\"active\":false}")
                .andExpect(status().isOk());
        send(get("/api/custom-fields/values/lead/" + lead), adminToken, null)
                .andExpect(jsonPath("$.data.fields[*].key").value(contains("visit_date")));
        String stored = jdbc.queryForObject("SELECT field_values::text FROM custom_field_values WHERE record_id = ? AND entity_type = 'lead'",
                String.class, lead);
        assertThat(stored).contains("High");
        send(put("/api/custom-fields/" + requiredField), adminToken, "{\"entityType\":\"lead\",\"label\":\"x\",\"fieldType\":\"text\"}")
                .andExpect(status().isBadRequest());

        // same access as the record itself
        User otherExec = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        send(get("/api/custom-fields/values/lead/" + lead), testTenants.tokenFor(otherExec), null).andExpect(status().isForbidden());
        send(get("/api/custom-fields/values/lead/" + lead), tenant.token(SERVICE_AGENT), null).andExpect(status().isForbidden());
        send(put("/api/custom-fields/values/lead/" + lead), tenant.token(FINANCE_APPROVER), "{}").andExpect(status().isForbidden());
        TestTenant other = testTenants.tenantWithAllRoles("Other Co " + System.nanoTime());
        send(get("/api/custom-fields/values/lead/" + lead), other.token(ADMIN), null).andExpect(status().isNotFound());
        send(get("/api/custom-fields").param("entityType", "lead"), other.token(ADMIN), null).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void opportunityFieldsFollowTheCustomersIndustry() throws Exception {
        send(post("/api/custom-fields/templates/real_estate/apply"), adminToken, null).andExpect(status().isOk());
        long buyer = customer("Home Buyer", "real_estate");
        long deal = idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + buyer + ",\"dealValue\":5000000,\"ownerId\":" + exec.getId() + "}"));
        send(put("/api/custom-fields/values/opportunity/" + deal), tenant.token(SALES_EXECUTIVE), "{\"possession_date\":\"2027-03-31\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("real_estate"))
                .andExpect(jsonPath("$.data.values.possession_date").value("2027-03-31"));
    }

    @Test
    void leadSourceReportCountsLeadsAndConversionsBySource() throws Exception {
        long a = lead("A", "referral", exec);
        lead("B", "referral", exec);
        lead("C", "website", exec);
        jdbc.update("UPDATE leads SET stage = 'converted' WHERE id = ?", a);

        send(get("/api/reports/lead-sources"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLeads").value(3))
                .andExpect(jsonPath("$.data.convertedLeads").value(1))
                .andExpect(jsonPath("$.data.bySource[0].key").value("referral"))
                .andExpect(jsonPath("$.data.bySource[0].label").value("Referral"))
                .andExpect(jsonPath("$.data.bySource[0].conversionRate").value(50.0));
        send(get("/api/reports/lead-sources"), tenant.token(MARKETING_EXECUTIVE), null).andExpect(status().isOk());
        send(get("/api/reports/lead-sources"), tenant.token(SALES_EXECUTIVE), null).andExpect(status().isForbidden());
        send(get("/api/analytics/export/lead-sources").param("format", "xlsx"), adminToken, null).andExpect(status().isOk());
        send(get("/api/analytics/export/lead-sources"), tenant.token(SERVICE_AGENT), null).andExpect(status().isForbidden());
    }

    private long customer(String name, String industry) throws Exception {
        return idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"" + name + "\",\"industry\":\"" + industry
                + "\",\"ownerId\":" + exec.getId() + "}"));
    }

    private long lead(String name, String source, User owner) throws Exception {
        return idOf(send(post("/api/leads"), adminToken, "{\"leadName\":\"" + name + "\",\"companyName\":\"" + name + " Co\",\"industry\":\"trading\""
                + ",\"source\":\"" + source + "\",\"ownerId\":" + owner.getId() + "}"));
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
