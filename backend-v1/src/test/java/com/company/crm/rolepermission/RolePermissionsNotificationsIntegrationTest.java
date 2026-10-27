package com.company.crm.rolepermission;

import com.company.crm.notification.service.WeeklyDigestService;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ROLE-8 (per-tenant permissions), VAL-2 (phone numbers) and NOT-1 (ticket update emails, weekly digest). */
class RolePermissionsNotificationsIntegrationTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WeeklyDigestService digestService;

    private TestTenant tenant;
    private String adminToken;

    @BeforeEach
    void setUp() {
        tenant = testTenants.tenantWithAllRoles("Perm Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
    }

    // ==================== ROLE-8 ====================

    @Test
    void adminSwitchesOffARolesPermissionAndTheApiFollows() throws Exception {
        long customer = customer("Archive Me", null);
        send(get("/api/role-permissions"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.role == 'sales_manager')].defaults[*]").value(hasItem("customers.delete")))
                .andExpect(jsonPath("$.data[?(@.role == 'admin')].customizable").value(contains(false)));

        send(put("/api/role-permissions/sales_manager"), adminToken, "{\"revoked\":[\"customers.delete\",\"reports.sales\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revoked").value(containsInAnyOrder("customers.delete", "reports.sales")))
                .andExpect(jsonPath("$.data.effective").value(not(hasItem("customers.delete"))));

        String manager = tenant.token(SALES_MANAGER);
        send(get("/api/role-permissions/mine"), manager, null).andExpect(jsonPath("$.data.revoked").value(hasItem("customers.delete")));
        send(patch("/api/customers/" + customer + "/archive"), manager, null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("PERMISSION_DISABLED"));
        send(get("/api/reports/sales"), manager, null).andExpect(status().isForbidden());
        send(get("/api/analytics/export/sales"), manager, null).andExpect(status().isForbidden());
        send(get("/api/customers/" + customer), manager, null).andExpect(status().isOk()); // the rest is unchanged
        send(patch("/api/customers/" + customer + "/archive"), adminToken, null).andExpect(status().isOk()); // admin never restricted
        send(get("/api/reports/sales"), tenant.token(EXECUTIVE_OWNER), null).andExpect(status().isOk()); // other roles unchanged

        // another tenant keeps the defaults
        TestTenant other = testTenants.tenantWithAllRoles("Other Perm " + System.nanoTime());
        send(get("/api/reports/sales"), other.token(SALES_MANAGER), null).andExpect(status().isOk());

        // switched back on
        send(put("/api/role-permissions/sales_manager"), adminToken, "{\"revoked\":[]}").andExpect(status().isOk());
        send(get("/api/reports/sales"), manager, null).andExpect(status().isOk());
        Integer audits = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'ROLE_PERMISSIONS_CHANGED' AND tenant_id = ?",
                Integer.class, tenant.tenant().getId());
        assertThat(audits).isEqualTo(2);
    }

    @Test
    void permissionsCanOnlyBeTakenAwayAndNotFromTheAdmin() throws Exception {
        send(put("/api/role-permissions/sales_manager"), adminToken, "{\"revoked\":[\"campaigns.manage\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("campaigns.manage")));
        send(put("/api/role-permissions/admin"), adminToken, "{\"revoked\":[\"customers.view\"]}").andExpect(status().isBadRequest());
        send(put("/api/role-permissions/nobody"), adminToken, "{\"revoked\":[]}").andExpect(status().isNotFound());
        send(put("/api/role-permissions/sales_executive"), tenant.token(SALES_MANAGER), "{\"revoked\":[]}").andExpect(status().isForbidden());
        send(get("/api/role-permissions"), tenant.token(SALES_MANAGER), null).andExpect(status().isForbidden());
    }

    // ==================== VAL-2 ====================

    @Test
    void phoneNumbersMustFitTheConfiguredCountry() throws Exception {
        for (String bad : new String[]{"12345", "+910123456789", "98765", "phone"}) {
            send(post("/api/customers"), adminToken, customerJson("Bad " + bad, bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.data.phone").value(containsString("+91")));
        }
        for (String good : new String[]{"+91 98765 43210", "9876543210", "098765 43210", "+1 415 555 0123", "+44 20 7946 0958"}) {
            send(post("/api/customers"), adminToken, customerJson("Good " + good, good)).andExpect(status().isOk());
        }
        send(post("/api/leads"), adminToken, "{\"leadName\":\"L\",\"companyName\":\"L Co\",\"industry\":\"trading\",\"source\":\"website\","
                + "\"contactPhone\":\"123\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.contactPhone").exists());
    }

    // ==================== NOT-1 ====================

    @Test
    void customerHearsAboutTicketProgress() throws Exception {
        String email = "buyer" + System.nanoTime() + "@buyer.test";
        long customer = customer("Ticket Buyer", email);
        User agent = tenant.user(SERVICE_AGENT);
        long ticket = idOf(send(post("/api/tickets"), adminToken, "{\"customerId\":" + customer + ",\"subject\":\"Pump noise\",\"priority\":\"medium\"}"));
        send(patch("/api/tickets/" + ticket + "/assign"), adminToken, "{\"technicianId\":" + agent.getId() + "}").andExpect(status().isOk());
        send(patch("/api/tickets/" + ticket + "/status"), adminToken, "{\"status\":\"in_progress\"}").andExpect(status().isOk());

        verify(mailService, atLeastOnce()).sendCustomerEmail(eq(email), eq("Update on your service request " + ticket + ": in progress"),
                ArgumentMatchers.contains("is now being worked on"), anyString());
    }

    @Test
    void weeklyDigestSummarisesTheWeekOncePerWeekAndCanBeSwitchedOff() throws Exception {
        User exec = tenant.user(SALES_EXECUTIVE);
        send(post("/api/leads"), adminToken, "{\"leadName\":\"Digest\",\"companyName\":\"Digest Co\",\"industry\":\"trading\",\"source\":\"website\","
                + "\"ownerId\":" + exec.getId() + ",\"followUpDate\":\"" + java.time.LocalDateTime.now().plusDays(2).withNano(0) + "\"}")
                .andExpect(status().isOk());
        jdbc.update("UPDATE leads SET created_at = now() - interval '2 days' WHERE owner_id = ?", exec.getId());

        WeeklyDigestService.Digest digest = digestService.compose(exec, java.time.LocalDate.now());
        assertThat(digest.subject()).contains("1 new lead(s)");
        assertThat(digest.body()).contains("New leads: 1").contains("1 lead follow-up(s) due").doesNotContain("@");

        assertThat(digestService.recipients()).contains(exec.getId());
        assertThat(digestService.send(exec.getId())).isTrue();
        assertThat(digestService.send(exec.getId())).isFalse(); // once per week
        verify(mailService, times(1)).sendCalendarEmail(eq(exec.getEmail()), ArgumentMatchers.startsWith("Your week in"), ArgumentMatchers.contains("New leads: 1"), isNull());

        String token = tenant.token(SALES_EXECUTIVE);
        send(get("/api/notifications/preferences"), token, null).andExpect(jsonPath("$.data.weeklyDigest").value(true));
        send(put("/api/notifications/preferences"), token, "{\"weeklyDigest\":false}").andExpect(jsonPath("$.data.weeklyDigest").value(false));
        assertThat(digestService.recipients()).doesNotContain(exec.getId());
        jdbc.update("UPDATE users SET last_digest_at = NULL WHERE id = ?", exec.getId());
        assertThat(digestService.send(exec.getId())).isFalse();
    }

    // ==================== helpers ====================

    private long customer(String name, String email) throws Exception {
        return idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"" + name + "\",\"industry\":\"trading\""
                + (email != null ? ",\"email\":\"" + email + "\"" : "") + "}"));
    }

    private static String customerJson(String name, String phone) {
        return "{\"companyName\":\"" + name + "\",\"industry\":\"trading\",\"phone\":\"" + phone + "\"}";
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
