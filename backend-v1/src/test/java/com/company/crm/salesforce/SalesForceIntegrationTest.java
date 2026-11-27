package com.company.crm.salesforce;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.company.crm.common.enums.RoleType.*;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Module 8: FR-8.1/8.2 + AC-8.1 targets and attainment, FR-8.5 + AC-8.2 commission on Won,
 * FR-8.3 activities, FR-8.4 attendance.
 */
class SalesForceIntegrationTest extends PostgresIntegrationTest {

    private TestTenant tenant;
    private String adminToken;
    private String managerToken;
    private User execA;
    private User execB;
    private User outsider; // sales executive in no team
    private long teamId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Sales Force Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        managerToken = tenant.token(SALES_MANAGER);
        execA = tenant.user(SALES_EXECUTIVE);
        execB = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        outsider = testTenants.createUser(tenant.tenant(), SALES_EXECUTIVE);
        teamId = idOf(send(post("/api/sales-teams"), adminToken, "{\"name\":\"West\",\"managerId\":" + tenant.user(SALES_MANAGER).getId() + "}"));
        send(put("/api/sales-teams/" + teamId + "/members"), adminToken, "{\"userIds\":[" + execA.getId() + "," + execB.getId() + "]}")
                .andExpect(status().isOk());
    }

    @Test
    void attainmentAndCommissionFollowWonDeals() throws Exception {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        String period = ",\"periodStart\":\"" + monthStart + "\",\"periodEnd\":\"" + monthStart.plusMonths(1).minusDays(1) + "\"";
        long revenueTarget = idOf(send(post("/api/sales-targets"), managerToken,
                "{\"userId\":" + execA.getId() + ",\"metric\":\"revenue\",\"targetValue\":10000" + period + "}"));
        send(post("/api/sales-targets"), managerToken, "{\"teamId\":" + teamId + ",\"metric\":\"units\",\"targetValue\":3" + period + "}")
                .andExpect(status().isOk());
        // Managers set targets for their people, not themselves or outsiders; salespeople can't set them.
        send(post("/api/sales-targets"), managerToken, "{\"userId\":" + tenant.user(SALES_MANAGER).getId() + ",\"metric\":\"revenue\",\"targetValue\":5" + period + "}")
                .andExpect(status().isForbidden());
        send(post("/api/sales-targets"), managerToken, "{\"userId\":" + outsider.getId() + ",\"metric\":\"revenue\",\"targetValue\":5" + period + "}")
                .andExpect(status().isForbidden());
        send(post("/api/sales-targets"), tenant.token(SALES_EXECUTIVE), "{\"userId\":" + execA.getId() + ",\"metric\":\"revenue\",\"targetValue\":5" + period + "}")
                .andExpect(status().isForbidden());
        send(post("/api/sales-targets"), adminToken, "{\"userId\":" + tenant.user(FINANCE_APPROVER).getId() + ",\"metric\":\"revenue\",\"targetValue\":5" + period + "}")
                .andExpect(status().isBadRequest());

        // FR-8.5 rules: big deals 10% (first), then the West team 5%.
        send(post("/api/commission-rules"), adminToken, "{\"name\":\"Big deals\",\"priority\":5,\"ratePercent\":10,\"minDealValue\":3000}")
                .andExpect(status().isOk());
        send(post("/api/commission-rules"), adminToken, "{\"name\":\"West team\",\"priority\":10,\"ratePercent\":5,\"teamId\":" + teamId + "}")
                .andExpect(status().isOk());
        send(post("/api/commission-rules"), managerToken, "{\"name\":\"X\",\"ratePercent\":5}").andExpect(status().isForbidden());

        long customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Buyer\",\"industry\":\"trading\"}"));
        long dealA = deal(customer, execA, 4000);
        long dealB = deal(customer, execB, 1000);

        // AC-8.1: attainment updates as soon as deals are won.
        send(get("/api/sales-targets"), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(jsonPath("$.data.length()").value(2)) // own + own team's
                .andExpect(jsonPath("$.data[?(@.id == " + revenueTarget + ")].actual").value(contains(4000.0)))
                .andExpect(jsonPath("$.data[?(@.id == " + revenueTarget + ")].attainmentPercent").value(contains(40.0)))
                .andExpect(jsonPath("$.data[?(@.teamId == " + teamId + ")].wonDeals").value(contains(2)))
                .andExpect(jsonPath("$.data[?(@.teamId == " + teamId + ")].attainmentPercent").value(contains(66.7)));

        // AC-8.2: commission recorded when Won, by the first matching rule.
        send(get("/api/commissions"), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].ruleName").value("Big deals"))
                .andExpect(jsonPath("$.data[0].amount").value(400.0))
                .andExpect(jsonPath("$.data[0].status").value("pending"));
        String finance = tenant.token(FINANCE_APPROVER);
        String entries = send(get("/api/commissions"), finance, null).andExpect(jsonPath("$.data.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        long entryB = ((Number) JsonPath.<java.util.List<Object>>read(entries, "$.data[?(@.opportunityId == " + dealB + ")].id").get(0)).longValue();
        send(get("/api/commissions").param("status", "pending"), managerToken, null).andExpect(jsonPath("$.data.length()").value(2));

        // Settlement: approve, then paid; salespeople can't.
        send(patch("/api/commissions/" + entryB + "/paid"), finance, null).andExpect(status().isBadRequest());
        send(patch("/api/commissions/" + entryB + "/approve"), tenant.token(SALES_EXECUTIVE), null).andExpect(status().isForbidden());
        send(patch("/api/commissions/" + entryB + "/approve"), finance, null).andExpect(jsonPath("$.data.status").value("approved"));
        send(patch("/api/commissions/" + entryB + "/paid"), finance, null)
                .andExpect(jsonPath("$.data.status").value("paid"))
                .andExpect(jsonPath("$.data.amount").value(50.0));

        // Re-opening a won deal voids its pending commission and lowers attainment; winning again recalculates.
        send(patch("/api/opportunities/" + dealA + "/stage"), adminToken, "{\"stage\":\"negotiation\",\"force\":true}").andExpect(status().isOk());
        send(get("/api/commissions").param("status", "void"), adminToken, null)
                .andExpect(jsonPath("$.data[*].opportunityId").value(contains((int) dealA)));
        send(get("/api/sales-targets"), adminToken, null).andExpect(jsonPath("$.data[?(@.id == " + revenueTarget + ")].actual").value(contains(0.0)));
        send(patch("/api/opportunities/" + dealA + "/stage"), adminToken, "{\"stage\":\"won\"}").andExpect(status().isOk());
        send(get("/api/commissions").param("status", "pending"), adminToken, null)
                .andExpect(jsonPath("$.data[*].opportunityId").value(contains((int) dealA)));
    }

    @Test
    void activitiesAreCountedPerSalesperson() throws Exception {
        String execToken = tenant.token(SALES_EXECUTIVE);
        long customer = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Field Co\",\"industry\":\"trading\",\"ownerId\":" + execA.getId() + "}"));
        send(post("/api/communications/calls"), execToken, "{\"customerId\":" + customer + ",\"direction\":\"outbound\",\"outcome\":\"connected\"}")
                .andExpect(status().isOk());
        send(post("/api/meetings"), execToken, "{\"customerId\":" + customer + ",\"title\":\"Demo\",\"startsAt\":\""
                + LocalDateTime.now().plusHours(2).withNano(0) + "\",\"endsAt\":\"" + LocalDateTime.now().plusHours(3).withNano(0) + "\"}")
                .andExpect(status().isOk());
        long quotation = idOf(send(post("/api/quotations"), execToken, "{\"customerId\":" + customer
                + ",\"lineItems\":[{\"productName\":\"Kit\",\"quantity\":1,\"unitPrice\":100,\"discountPercent\":0}]}"));
        send(patch("/api/quotations/" + quotation + "/send"), execToken, null).andExpect(status().isOk());
        send(post("/api/sales-activities/visits"), execToken, "{\"customerId\":" + customer + ",\"notes\":\"Site survey\",\"latitude\":18.52,\"longitude\":73.85}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerName").value("Field Co"));
        send(post("/api/sales-activities/visits"), execToken, "{\"occurredAt\":\"" + LocalDateTime.now().plusDays(1).withNano(0) + "\"}")
                .andExpect(status().isBadRequest());

        String today = LocalDate.now().toString();
        String nextWeek = LocalDate.now().plusDays(7).toString();
        send(get("/api/sales-activities/summary").param("from", today).param("to", nextWeek), managerToken, null)
                .andExpect(jsonPath("$.data[0].userId").value(execA.getId()))
                .andExpect(jsonPath("$.data[0].calls").value(1))
                .andExpect(jsonPath("$.data[0].meetings").value(1))
                .andExpect(jsonPath("$.data[0].quotationsSent").value(1))
                .andExpect(jsonPath("$.data[0].visits").value(1))
                .andExpect(jsonPath("$.data[0].total").value(4))
                .andExpect(jsonPath("$.data[?(@.userId == " + outsider.getId() + ")]").isEmpty()); // not in the manager's team
        send(get("/api/sales-activities/summary").param("from", today).param("to", nextWeek), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(jsonPath("$.data.length()").value(1));
        send(get("/api/sales-activities/visits").param("from", today).param("to", today).param("userId", String.valueOf(outsider.getId())), managerToken, null)
                .andExpect(status().isForbidden());
    }

    @Test
    void attendanceCheckInAndOut() throws Exception {
        String execToken = tenant.token(SALES_EXECUTIVE);
        send(post("/api/attendance/check-in"), execToken, "{\"latitude\":18.5204,\"longitude\":73.8567}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkInLat").value(18.5204));
        send(post("/api/attendance/check-in"), execToken, null).andExpect(status().isConflict());
        send(get("/api/attendance/me"), execToken, null).andExpect(jsonPath("$.data.current.checkOutAt").doesNotExist());
        send(post("/api/attendance/check-out"), execToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hours").isNumber());
        send(post("/api/attendance/check-out"), execToken, null).andExpect(status().isBadRequest());
        send(post("/api/attendance/check-in"), tenant.token(MARKETING_EXECUTIVE), "{\"latitude\":10}").andExpect(status().isBadRequest());

        String today = LocalDate.now().toString();
        send(get("/api/attendance").param("from", today).param("to", today), managerToken, null)
                .andExpect(jsonPath("$.data[*].userId").value(hasItem(execA.getId().intValue())));
        send(get("/api/attendance").param("from", today).param("to", today), execToken, null).andExpect(status().isForbidden());
    }

    private long deal(long customer, User owner, int value) throws Exception {
        long id = idOf(send(post("/api/opportunities"), adminToken, "{\"customerId\":" + customer + ",\"dealValue\":" + value + ",\"ownerId\":" + owner.getId() + "}"));
        send(patch("/api/opportunities/" + id + "/stage"), adminToken, "{\"stage\":\"won\",\"force\":true}").andExpect(status().isOk());
        return id;
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
