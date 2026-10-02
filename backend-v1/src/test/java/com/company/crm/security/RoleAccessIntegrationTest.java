package com.company.crm.security;

import com.company.crm.common.enums.RoleType;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.company.crm.common.enums.RoleType.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Per-role authorization over HTTP: each role gets 200 on the endpoints it may read and 403 on
 * every other one; and no role can reach another tenant's records by guessing ids.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleAccessIntegrationTest extends PostgresIntegrationTest {

    private record Rule(String path, Set<RoleType> allowed) {}

    /** Which roles may GET each endpoint (mirrors the controllers' @PreAuthorize). */
    private static final List<Rule> READ_MATRIX = List.of(
            new Rule("/api/customers", Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE, MARKETING_EXECUTIVE, SERVICE_AGENT, FINANCE_APPROVER, EXECUTIVE_OWNER)),
            new Rule("/api/leads", Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE, MARKETING_EXECUTIVE, FINANCE_APPROVER, EXECUTIVE_OWNER)),
            new Rule("/api/opportunities", Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE, FINANCE_APPROVER, EXECUTIVE_OWNER)),
            new Rule("/api/quotations", Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE, FINANCE_APPROVER, EXECUTIVE_OWNER)),
            new Rule("/api/tickets", Set.of(ADMIN, SALES_MANAGER, SERVICE_AGENT, EXECUTIVE_OWNER)),
            new Rule("/api/campaigns", Set.of(ADMIN, MARKETING_EXECUTIVE, EXECUTIVE_OWNER)),
            new Rule("/api/products", Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE)),
            new Rule("/api/subscriptions/current", Set.of(ADMIN)),
            new Rule("/api/dashboard/tenant-admin-status", Set.of(ADMIN)),
            new Rule("/api/super-admin/tenants", Set.of(SUPER_ADMIN)),
            new Rule("/api/tenants", Set.of(SUPER_ADMIN)),
            new Rule("/api/dashboard/super-admin", Set.of(SUPER_ADMIN)),
            new Rule("/api/users", EnumSet.allOf(RoleType.class)),
            // Dashboard summaries: same roles as the module's list
            new Rule("/api/customers/summary", Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE, MARKETING_EXECUTIVE, SERVICE_AGENT, FINANCE_APPROVER, EXECUTIVE_OWNER)),
            new Rule("/api/leads/summary", Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE, MARKETING_EXECUTIVE, FINANCE_APPROVER, EXECUTIVE_OWNER)),
            new Rule("/api/opportunities/kpis", Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE, FINANCE_APPROVER, EXECUTIVE_OWNER)),
            new Rule("/api/quotations/summary", Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE, FINANCE_APPROVER, EXECUTIVE_OWNER)),
            new Rule("/api/tickets/summary", Set.of(ADMIN, SALES_MANAGER, SERVICE_AGENT, EXECUTIVE_OWNER)),
            new Rule("/api/campaigns/summary", Set.of(ADMIN, MARKETING_EXECUTIVE, EXECUTIVE_OWNER))
    );

    private TestTenant acme;
    private TestTenant globex;
    private String superAdminToken;

    @BeforeAll
    void createTenants() {
        acme = testTenants.tenantWithAllRoles("Acme " + System.nanoTime());
        globex = testTenants.tenantWithAllRoles("Globex " + System.nanoTime());
        superAdminToken = testTenants.superAdminToken();
    }

    Stream<Arguments> readMatrix() {
        return READ_MATRIX.stream().flatMap(rule -> Arrays.stream(RoleType.values())
                .map(role -> Arguments.of(role, rule.path(), rule.allowed().contains(role))));
    }

    @ParameterizedTest(name = "{0} GET {1} → allowed={2}")
    @MethodSource("readMatrix")
    void eachRoleCanReadExactlyItsEndpoints(RoleType role, String path, boolean allowed) throws Exception {
        String token = role == SUPER_ADMIN ? superAdminToken : acme.token(role);
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(allowed ? status().isOk() : status().isForbidden());
    }

    @Test
    void writeEndpointsRejectRolesWithoutWriteAccess() throws Exception {
        // Read-only roles can't create customers, leads, campaigns, tickets or users. Bodies are
        // valid on purpose: Spring validates the body before method security, so an invalid body
        // would answer 400 and never exercise the role check.
        String customer = "{\"companyName\":\"X\",\"industry\":\"trading\"}";
        String lead = "{\"leadName\":\"L\",\"companyName\":\"X\",\"industry\":\"trading\",\"source\":\"referral\"}";
        String user = "{\"fullName\":\"New Person\",\"email\":\"new.person@test.local\",\"password\":\"Secret123!\",\"role\":\"sales_executive\"}";
        expectForbidden(post("/api/customers"), acme.token(MARKETING_EXECUTIVE), customer);
        expectForbidden(post("/api/customers"), acme.token(EXECUTIVE_OWNER), customer);
        expectForbidden(post("/api/campaigns"), acme.token(SALES_EXECUTIVE), "{\"name\":\"X\",\"channel\":\"email\"}");
        expectForbidden(post("/api/leads"), acme.token(FINANCE_APPROVER), lead);
        expectForbidden(post("/api/users"), acme.token(SALES_MANAGER), user);
        expectForbidden(post("/api/tickets"), acme.token(FINANCE_APPROVER), "{\"customerId\":1,\"subject\":\"S\",\"priority\":\"medium\"}");
    }

    @Test
    void noRoleCanReachAnotherTenantsRecordsById() throws Exception {
        String globexAdmin = globex.token(ADMIN);
        long customerId = createAndGetId(post("/api/customers"), globexAdmin,
                "{\"companyName\":\"Globex Secret Co\",\"industry\":\"trading\"}");
        long campaignId = createAndGetId(post("/api/campaigns"), globexAdmin, "{\"name\":\"Globex launch\",\"channel\":\"email\"}");
        long ticketId = createAndGetId(post("/api/tickets"), globexAdmin,
                "{\"customerId\":" + customerId + ",\"subject\":\"Broken\",\"priority\":\"medium\"}");
        long leadId = createAndGetId(post("/api/leads"), globexAdmin,
                "{\"leadName\":\"Lead\",\"companyName\":\"Globex Secret Co\",\"industry\":\"trading\",\"source\":\"referral\"}");

        String acmeAdmin = acme.token(ADMIN);
        // Reads
        expect404(get("/api/customers/" + customerId), acmeAdmin);
        expect404(get("/api/campaigns/" + campaignId), acmeAdmin);
        expect404(get("/api/tickets/" + ticketId), acmeAdmin);
        expect404(get("/api/leads/" + leadId), acmeAdmin);
        expect404(get("/api/customers/" + customerId), acme.token(SALES_MANAGER));
        // Writes and deletes
        mockMvc.perform(put("/api/customers/" + customerId).header("Authorization", "Bearer " + acmeAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"companyName\":\"Hijacked\",\"industry\":\"trading\"}"))
                .andExpect(status().isNotFound());
        expect404(delete("/api/campaigns/" + campaignId), acmeAdmin);
        expect404(delete("/api/leads/" + leadId), acmeAdmin);
        // References to another tenant's records are rejected too (opportunity on a foreign customer).
        mockMvc.perform(post("/api/opportunities").header("Authorization", "Bearer " + acmeAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":" + customerId + "}"))
                .andExpect(status().isBadRequest());

        // And the owner can still see their own record.
        mockMvc.perform(get("/api/customers/" + customerId).header("Authorization", "Bearer " + globexAdmin))
                .andExpect(status().isOk());
    }

    private void expectForbidden(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                 String token, String body) throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    private void expect404(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                           String token) throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    }

    private long createAndGetId(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                String token, String body) throws Exception {
        String response = mockMvc.perform(request.header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }
}
