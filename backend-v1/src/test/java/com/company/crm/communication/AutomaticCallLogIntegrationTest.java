package com.company.crm.communication;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static com.company.crm.common.enums.RoleType.ADMIN;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-5.4b: calls are logged automatically from Twilio Voice status callbacks. */
class AutomaticCallLogIntegrationTest extends PostgresIntegrationTest {

    private TestTenant tenant;
    private String adminToken;
    private String phone10;
    private long customerId;

    @BeforeEach
    void setUp() throws Exception {
        long n = System.nanoTime();
        tenant = testTenants.tenantWithAllRoles("Calls Co " + n);
        adminToken = tenant.token(ADMIN);
        phone10 = "8" + String.format("%09d", n % 1_000_000_000L);
        String body = mockMvc.perform(post("/api/customers").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Caller Co\",\"industry\":\"trading\",\"phone\":\"" + phone10 + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        customerId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        when(twilioClient.isValidSignature(anyString(), anyMap(), any())).thenReturn(true);
    }

    @Test
    void finishedCallsAreLoggedOnceAgainstTheMatchingCustomer() throws Exception {
        String sid = "CA" + System.nanoTime();
        voice(sid, "+91" + phone10, "+911234567890", "inbound", "ringing", null).andExpect(status().isNoContent());
        voice(sid, "+91" + phone10, "+911234567890", "inbound", "completed", "125").andExpect(status().isNoContent());
        voice(sid, "+91" + phone10, "+911234567890", "inbound", "completed", "125").andExpect(status().isNoContent()); // retry
        voice("CA" + System.nanoTime(), "+911234567890", "+91" + phone10, "outbound-api", "busy", "0").andExpect(status().isNoContent());
        voice("CA" + System.nanoTime(), "+917777777777", "+911234567890", "inbound", "completed", "30").andExpect(status().isNoContent()); // unknown

        mockMvc.perform(get("/api/communications").param("customerId", String.valueOf(customerId)).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.callOutcome == 'connected')].direction").value(org.hamcrest.Matchers.contains("inbound")))
                .andExpect(jsonPath("$.data[?(@.callOutcome == 'connected')].callDurationSeconds").value(org.hamcrest.Matchers.contains(125)))
                .andExpect(jsonPath("$.data[?(@.callOutcome == 'busy')].direction").value(org.hamcrest.Matchers.contains("outbound")))
                .andExpect(jsonPath("$.data[0].channel").value("call"))
                .andExpect(jsonPath("$.data[0].createdByName").doesNotExist());
    }

    @Test
    void unsignedCallbacksAreRejected() throws Exception {
        when(twilioClient.isValidSignature(anyString(), anyMap(), any())).thenReturn(false);
        voice("CA1", "+91" + phone10, "+911234567890", "inbound", "completed", "10").andExpect(status().isForbidden());
    }

    private ResultActions voice(String sid, String from, String to, String direction, String status, String duration) throws Exception {
        var request = post("/api/communications/webhooks/twilio/voice").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-Twilio-Signature", "sig")
                .param("CallSid", sid).param("From", from).param("To", to)
                .param("Direction", direction).param("CallStatus", status);
        if (duration != null) {
            request.param("CallDuration", duration);
        }
        return mockMvc.perform(request);
    }
}
