package com.company.crm.security;

import com.company.crm.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deny-by-default guard: every API handler must declare who may call it with @PreAuthorize
 * (method or class level). Authentication alone is never enough. The only exceptions are the
 * deliberately public endpoints listed here — adding one is a conscious, reviewed decision.
 */
class EndpointAuthorizationCoverageTest extends PostgresIntegrationTest {

    /** "METHOD path-pattern" of handlers that are public by design. */
    private static final Set<String> PUBLIC_BY_DESIGN = Set.of(
            "POST /auth/login",
            "POST /auth/signup",
            "POST /auth/logout",
            "POST /auth/verify-email",
            "POST /auth/resend-verification",
            "POST /auth/forgot-password",
            "POST /auth/reset-password",
            "POST /auth/refresh",              // API-G01: the refresh token in the body is the credential
            "GET /auth/password-policy",       // SEC-1b: the password rules for the sign-up and reset forms
            "POST /api/public/quotations/{token}/respond", // NAV-2: the view-link token is the credential
            "POST /auth/mfa/verify",           // SEC-1c: second sign-in step, authenticated by the one-time challenge token
            "POST /auth/mfa/setup",
            "POST /auth/mfa/setup/confirm",
            "GET /api/plans/prices",          // marketing pricing page
            "POST /api/payments/webhook",     // payment provider; authenticated by signature
            "POST /api/communications/webhooks/twilio/inbound",  // Twilio; authenticated by X-Twilio-Signature
            "POST /api/communications/webhooks/twilio/status",   // Twilio; authenticated by X-Twilio-Signature
            "POST /api/communications/webhooks/twilio/voice",    // FR-5.4b Twilio call callback; X-Twilio-Signature
            "GET /api/public/calendar/{token}",                  // FR-5.5c per-user calendar feed; token is the credential
            "GET /api/public/support/{token}",                   // FR-6.1 customer support form; token is the credential
            "POST /api/public/support/{token}",
            "GET /api/public/feedback/{token}",                  // FR-6.5 customer feedback link; token is the credential
            "POST /api/public/feedback/{token}",
            "GET /api/public/campaigns/o/{token}",               // FR-7.4 open pixel; recipient token is the credential
            "GET /api/public/campaigns/c/{token}/{link}",        // FR-7.4 tracked link
            "GET /api/public/campaigns/u/{token}",               // FR-7.1c unsubscribe
            "POST /api/public/campaigns/u/{token}",
            "GET /api/public/quotations/{token}",                // FR-4.5 customer view link; token is the credential
            "GET /api/public/quotations/{token}/pdf",
            "POST /api/integrations/payment",                    // API-G12 payment webhook; signature
            "POST /api/integrations/whatsapp-webhook",           // API-G12 Twilio inbound; X-Twilio-Signature
            "GET /api/integrations/erp/customers",               // API-G12 ERP sync; X-Api-Key is the credential
            "GET /api/integrations/erp/won-deals",
            "GET /api/integrations/erp/quotations",
            "PUT /api/integrations/erp/customers/{customerId}/erp-code",
            "PUT /api/integrations/erp/products"
    );

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyApiHandlerDeclaresItsAllowedRoles() {
        List<String> unprotected = new ArrayList<>();
        int checked = 0;

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("com.company.crm")) {
                continue; // framework endpoints (error page, springdoc)
            }
            for (String signature : signatures(entry.getKey())) {
                checked++;
                boolean annotated = AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PreAuthorize.class)
                        || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), PreAuthorize.class);
                if (!annotated && !PUBLIC_BY_DESIGN.contains(signature)) {
                    unprotected.add(signature + "  →  " + handler.getShortLogMessage());
                }
            }
        }

        assertThat(checked).as("handlers inspected").isGreaterThan(50);
        assertThat(unprotected).as("API handlers without @PreAuthorize").isEmpty();
    }

    private static List<String> signatures(RequestMappingInfo info) {
        List<String> result = new ArrayList<>();
        Set<String> patterns = info.getPatternValues();
        var methods = info.getMethodsCondition().getMethods();
        for (String pattern : patterns) {
            if (methods.isEmpty()) {
                result.add("ANY " + pattern);
            }
            methods.forEach(method -> result.add(method.name() + " " + pattern));
        }
        return result;
    }
}
