package com.company.crm;

import com.company.crm.common.mail.MailService;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.payment.gateway.PaymentGateway;
import com.company.crm.payment.gateway.PaymentGateway.GatewayOrder;
import com.company.crm.payment.gateway.PaymentGateway.WebhookEvent;
import com.company.crm.common.enums.SubscriptionStatus;
import com.company.crm.subscription.entity.Subscription;
import com.company.crm.subscription.repository.SubscriptionRepository;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole SaaS journey over HTTP against a real PostgreSQL:
 * signup → verify email → trial → hit plan limit → pay → upgraded → lapse → read-only → tenant deactivated.
 * Only the edges are mocked: outgoing mail and the payment provider.
 */
@SpringBootTest(properties = "app.subscription.trial-plan=starter")
@AutoConfigureMockMvc
class SaasFlowIntegrationTest {

    private static final String EMAIL = "owner@acme.test";
    private static final String PASSWORD = "Secret123!";

    private static EmbeddedPostgres postgres;

    @Autowired private MockMvc mockMvc;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private MailService mailService;
    @MockitoBean private PaymentGateway paymentGateway;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        try {
            postgres = EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void stop() throws IOException {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void signupVerifyTrialLimitPayExpireDeactivate() throws Exception {
        // ---- 1. Signup: tenant + pending admin, verification mail sent after commit
        postJson("/auth/signup", null, """
                {"name":"Asha Owner","email":"%s","password":"%s",
                 "organizationName":"Acme Pvt Ltd","bankAccountNumber":"123456789"}
                """.formatted(EMAIL, PASSWORD))
                .andExpect(status().isOk());

        ArgumentCaptor<String> verificationToken = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendVerificationEmail(eq(EMAIL), anyString(), verificationToken.capture());

        // ---- 2. Unverified users can't log in
        postJson("/auth/login", null, loginBody())
                .andExpect(status().isForbidden());

        // ---- 3. Verify email → starts the trial; the link is single-use
        postJson("/auth/verify-email", null, "{\"token\":\"" + verificationToken.getValue() + "\"}")
                .andExpect(status().isOk());
        postJson("/auth/verify-email", null, "{\"token\":\"" + verificationToken.getValue() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_INVALID"));

        // ---- 4. Login → JWT carries tenant + subscription claims
        String jwt = login();
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(jwt).getPayload();
        assertThat(claims.get("subscriptionStatus", String.class)).isEqualTo("trial");
        assertThat(claims.get("plan", String.class)).isEqualTo("starter");

        mockMvc.perform(get("/api/subscriptions/current").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("trial"))
                .andExpect(jsonPath("$.data.maxCampaigns").value(5))
                // "My Plan" page fields
                .andExpect(jsonPath("$.data.price").value(1500.00))
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.daysRemaining").value(14))
                .andExpect(jsonPath("$.data.totalDays").value(14))
                .andExpect(jsonPath("$.data.autoRenew").value(false))
                .andExpect(jsonPath("$.data.usage.campaigns.limit").value(5))
                .andExpect(jsonPath("$.data.usage.campaigns.used").value(0))
                .andExpect(jsonPath("$.data.usage.users.used").value(1))
                .andExpect(jsonPath("$.data.features[0]").value("Customer 360, leads and sales pipeline"))
                .andExpect(jsonPath("$.data.canUpgrade").value(true));

        // Plan comparison: starter is current, business/enterprise are upgrades.
        String plansBody = mockMvc.perform(get("/api/subscriptions/plans").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("starter"))
                .andExpect(jsonPath("$.data[0].current").value(true))
                .andExpect(jsonPath("$.data[0].upgrade").value(false))
                .andExpect(jsonPath("$.data[1].code").value("business"))
                .andExpect(jsonPath("$.data[1].upgrade").value(true))
                .andExpect(jsonPath("$.data[1].annualPrice").value(37200.00))
                .andReturn().getResponse().getContentAsString();
        Integer starterPlanId = JsonPath.read(plansBody, "$.data[0].id");
        Integer businessPlanId = JsonPath.read(plansBody, "$.data[1].id");

        // "Upgrading" to the plan you're already on is rejected before any payment is created.
        postJson("/api/subscriptions/upgrade", jwt, "{\"planId\":" + starterPlanId + ",\"billingCycle\":\"monthly\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PLAN_CHANGE"));

        // ---- 5. Starter allows 5 campaigns; the 6th hits the plan limit
        for (int i = 1; i <= 5; i++) {
            createCampaign(jwt, i).andExpect(status().isOk());
        }
        createCampaign(jwt, 6)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PLAN_LIMIT_EXCEEDED"));

        // ---- 6. Pay for Business monthly (provider mocked) → subscription active on Business
        when(paymentGateway.provider()).thenReturn("fake");
        when(paymentGateway.checkoutKey()).thenReturn("pk_test");
        when(paymentGateway.createOrder(anyLong(), anyString(), anyString()))
                .thenAnswer(inv -> new GatewayOrder("order_test_1", inv.getArgument(1), inv.getArgument(0)));
        when(paymentGateway.isPaymentSignatureValid("order_test_1", "pay_test_1", "sig_ok")).thenReturn(true);

        // Upgrade from the My Plan page: validated, then a checkout order — the plan doesn't change yet.
        postJson("/api/subscriptions/upgrade", jwt, "{\"planId\":" + businessPlanId + ",\"billingCycle\":\"monthly\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("order_test_1"))
                .andExpect(jsonPath("$.data.amount").value(390000)); // ₹3,900.00 in paise (V4 price)
        mockMvc.perform(get("/api/subscriptions/current").header("Authorization", "Bearer " + jwt))
                .andExpect(jsonPath("$.data.plan").value("starter"))
                .andExpect(jsonPath("$.data.status").value("trial"));

        postJson("/api/payments/verify", jwt, """
                {"razorpay_order_id":"order_test_1","razorpay_payment_id":"pay_test_1","razorpay_signature":"sig_ok"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/subscriptions/current").header("Authorization", "Bearer " + jwt))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.plan").value("business"))
                .andExpect(jsonPath("$.data.billingCycle").value("monthly"));
        createCampaign(jwt, 6).andExpect(status().isOk());

        // ---- 7. Lapse past the grace period → reads still work, writes get 402
        Long tenantId = userRepository.findByEmail(EMAIL).orElseThrow().getTenant().getId();
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId).orElseThrow();
        subscription.setExpiresAt(LocalDateTime.now().minusDays(30));
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/campaigns").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
        createCampaign(jwt, 7)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errorCode").value("SUBSCRIPTION_INACTIVE"));

        // ---- 8. Platform deactivates the tenant → the existing JWT stops working, login is refused
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        tenant.setStatus(AccountStatus.DEACTIVATED);
        tenantRepository.save(tenant);

        mockMvc.perform(get("/api/campaigns").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
        postJson("/auth/login", null, loginBody())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TENANT_INACTIVE"));
    }

    /**
     * /verify and the provider webhook for the same payment racing each other must activate the
     * subscription exactly once. The signature check is slowed down so the webhook deterministically
     * arrives while /verify is mid-flight — without the row lock this grants two billing periods.
     */
    @Test
    void concurrentVerifyAndWebhook_activateSubscriptionOnlyOnce() throws Exception {
        String email = "race@acme.test";
        String jwt = signupVerifyAndLogin(email);
        Long tenantId = userRepository.findByEmail(email).orElseThrow().getTenant().getId();

        when(paymentGateway.provider()).thenReturn("fake");
        when(paymentGateway.createOrder(anyLong(), anyString(), anyString()))
                .thenAnswer(inv -> new GatewayOrder("order_race", inv.getArgument(1), inv.getArgument(0)));
        when(paymentGateway.isPaymentSignatureValid("order_race", "pay_race", "sig_ok")).thenAnswer(inv -> {
            Thread.sleep(500);
            return true;
        });
        when(paymentGateway.parseWebhook(anyString(), anyString())).thenReturn(Optional.of(
                new WebhookEvent(WebhookEvent.Type.PAYMENT_CAPTURED, "order_race", "pay_race", null)));

        postJson("/api/payments/create-order", jwt, "{\"plan\":\"business\",\"billingCycle\":\"monthly\"}")
                .andExpect(status().isOk());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> verifyCall = pool.submit(() -> postJson("/api/payments/verify", jwt, """
                    {"razorpay_order_id":"order_race","razorpay_payment_id":"pay_race","razorpay_signature":"sig_ok"}
                    """).andReturn().getResponse().getStatus());
            Thread.sleep(150); // let /verify take the row first, then fire the webhook into its window
            Future<Integer> webhookCall = pool.submit(() -> mockMvc.perform(post("/api/payments/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Razorpay-Signature", "sig")
                            .content("{\"event\":\"payment.captured\"}"))
                    .andReturn().getResponse().getStatus());

            assertThat(verifyCall.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(webhookCall.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        // Exactly one monthly period from activation — not two.
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId).orElseThrow();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getExpiresAt())
                .isCloseTo(LocalDateTime.now().plusMonths(1), within(1, ChronoUnit.MINUTES));

        // A late duplicate webhook is a no-op too.
        mockMvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "sig").content("{\"event\":\"payment.captured\"}"))
                .andExpect(status().isOk());
        assertThat(subscriptionRepository.findByTenantId(tenantId).orElseThrow().getExpiresAt())
                .isEqualTo(subscription.getExpiresAt());
    }

    private String signupVerifyAndLogin(String email) throws Exception {
        postJson("/auth/signup", null, """
                {"name":"Owner","email":"%s","password":"%s",
                 "organizationName":"Race Co","bankAccountNumber":"123456789"}
                """.formatted(email, PASSWORD))
                .andExpect(status().isOk());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendVerificationEmail(eq(email), anyString(), token.capture());
        postJson("/auth/verify-email", null, "{\"token\":\"" + token.getValue() + "\"}")
                .andExpect(status().isOk());

        String body = postJson("/auth/login", null, "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    private String login() throws Exception {
        String body = postJson("/auth/login", null, loginBody())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    private String loginBody() {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD);
    }

    private ResultActions createCampaign(String jwt, int n) throws Exception {
        return postJson("/api/campaigns", jwt, "{\"name\":\"Campaign " + n + "\",\"channel\":\"email\"}");
    }

    private ResultActions postJson(String url, String jwt, String json) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(json);
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        return mockMvc.perform(request);
    }
}
