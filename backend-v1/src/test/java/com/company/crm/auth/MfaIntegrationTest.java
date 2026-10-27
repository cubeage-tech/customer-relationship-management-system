package com.company.crm.auth;

import com.company.crm.auth.mfa.Totp;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SEC-1c: two-step sign-in — required for admins, optional for everyone else. */
@TestPropertySource(properties = "app.security.mfa.required-roles=super_admin,admin")
class MfaIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;

    private TestTenant tenant;
    private User admin;
    private User exec;
    private long baseStep;

    @BeforeEach
    void setUp() {
        tenant = testTenants.tenantWithAllRoles("MFA Co " + System.nanoTime());
        admin = withPassword(tenant.user(ADMIN));
        exec = withPassword(tenant.user(SALES_EXECUTIVE));
        baseStep = Totp.step(Instant.now());
    }

    @Test
    void anAdminMustSetUpTwoStepSignInThenUseIt() throws Exception {
        String first = login(admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.mfaSetupRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        String challenge = JsonPath.read(first, "$.data.mfaToken");

        String setup = postJson("/auth/mfa/setup", "{\"mfaToken\":\"" + challenge + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpauthUri").value(startsWith("otpauth://totp/SmartCRM:")))
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(setup, "$.data.secret");
        postJson("/auth/mfa/setup/confirm", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"000000\"}").andExpect(status().isBadRequest());
        String confirmed = postJson("/auth/mfa/setup/confirm", "{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + code(secret, 0) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.recoveryCodes.length()").value(8))
                .andReturn().getResponse().getContentAsString();
        List<String> recovery = JsonPath.read(confirmed, "$.data.recoveryCodes");
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getMfaSecret()).isEqualTo(secret); // decrypted by the converter

        // next sign-in: password, then a code; a used code can't be replayed
        String verify = challengeOf(login(admin).andExpect(jsonPath("$.data.mfaRequired").value(true)));
        postJson("/auth/mfa/verify", "{\"mfaToken\":\"" + verify + "\",\"code\":\"" + code(secret, 0) + "\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("MFA_INVALID_CODE"));
        postJson("/auth/mfa/verify", "{\"mfaToken\":\"" + verify + "\",\"code\":\"" + code(secret, 1) + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString()).andExpect(jsonPath("$.data.user.role").value("admin"));
        postJson("/auth/mfa/verify", "{\"mfaToken\":\"" + verify + "\",\"code\":\"" + code(secret, 1) + "\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("MFA_CHALLENGE_EXPIRED"));

        // a recovery code works once
        String again = challengeOf(login(admin));
        postJson("/auth/mfa/verify", "{\"mfaToken\":\"" + again + "\",\"code\":\"" + recovery.get(0).toUpperCase() + "\"}").andExpect(status().isOk());
        String third = challengeOf(login(admin));
        postJson("/auth/mfa/verify", "{\"mfaToken\":\"" + third + "\",\"code\":\"" + recovery.get(0) + "\"}").andExpect(status().isUnauthorized());

        // a required role can't switch it off
        send(get("/api/mfa"), tenant.token(ADMIN), null).andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.required").value(true)).andExpect(jsonPath("$.data.recoveryCodesLeft").value(7));
        send(post("/api/mfa/disable"), tenant.token(ADMIN), "{\"code\":\"" + code(secret, 2) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void wrongCodesCountTowardsTheSignInLockout() throws Exception {
        String token = challengeOf(login(admin));
        String secret = JsonPath.read(postJson("/auth/mfa/setup", "{\"mfaToken\":\"" + token + "\"}").andReturn().getResponse().getContentAsString(), "$.data.secret");
        postJson("/auth/mfa/setup/confirm", "{\"mfaToken\":\"" + token + "\",\"code\":\"" + code(secret, 0) + "\"}").andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            String c = challengeOf(login(admin));
            postJson("/auth/mfa/verify", "{\"mfaToken\":\"" + c + "\",\"code\":\"123456\"}").andExpect(status().isUnauthorized());
        }
        login(admin).andExpect(status().isTooManyRequests());
    }

    @Test
    void otherRolesMayTurnItOnAndAnAdminCanReset() throws Exception {
        login(exec).andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString()); // not required
        String execToken = tenant.token(SALES_EXECUTIVE);
        String secret = JsonPath.read(send(post("/api/mfa/setup"), execToken, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.secret");
        send(post("/api/mfa/enable"), execToken, "{\"code\":\"" + code(secret, 0) + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.recoveryCodes.length()").value(8));
        login(exec).andExpect(jsonPath("$.data.mfaRequired").value(true));

        // lost phone: the tenant admin resets it; a user of another tenant can't be reset
        send(post("/api/mfa/users/" + exec.getId() + "/reset"), tenant.token(SALES_MANAGER), null).andExpect(status().isForbidden());
        TestTenant other = testTenants.tenantWithAllRoles("Other MFA " + System.nanoTime());
        send(post("/api/mfa/users/" + exec.getId() + "/reset"), other.token(ADMIN), null).andExpect(status().isNotFound());
        send(post("/api/mfa/users/" + exec.getId() + "/reset"), tenant.token(ADMIN), null).andExpect(status().isOk());
        login(exec).andExpect(jsonPath("$.data.token").isString());

        // optional MFA can be switched off with a code
        String secret2 = JsonPath.read(send(post("/api/mfa/setup"), execToken, null).andReturn().getResponse().getContentAsString(), "$.data.secret");
        send(post("/api/mfa/enable"), execToken, "{\"code\":\"" + code(secret2, 0) + "\"}").andExpect(status().isOk());
        send(post("/api/mfa/disable"), execToken, "{\"code\":\"999999\"}").andExpect(status().isBadRequest());
        send(post("/api/mfa/disable"), execToken, "{\"code\":\"" + code(secret2, 1) + "\"}").andExpect(status().isOk());
        send(get("/api/mfa"), execToken, null).andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void totpMatchesTheRfc6238TestVector() {
        // RFC 6238 appendix B, SHA-1, T = 59 s: 94287082 (8 digits) → last 6 digits 287082
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"; // "12345678901234567890"
        assertThat(Totp.code(secret, 59 / 30)).isEqualTo("287082");
    }

    // ==================== helpers ====================

    /**
     * The code for the test's starting time step + {@code ahead}: each is a distinct step, and all stay
     * inside the server's ±1-step window even if a 30-second boundary passes during the test.
     */
    private String code(String secret, int ahead) {
        return Totp.code(secret, baseStep + ahead);
    }

    private User withPassword(User u) {
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userRepository.save(u);
    }

    private ResultActions login(User u) throws Exception {
        return postJson("/auth/login", "{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}");
    }

    private static String challengeOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.mfaToken");
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }
}
