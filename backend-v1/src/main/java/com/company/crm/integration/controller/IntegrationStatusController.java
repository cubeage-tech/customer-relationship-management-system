package com.company.crm.integration.controller;

import com.company.crm.ai.llm.LlmGateway;
import com.company.crm.common.config.PaymentConfig.PaymentProperties;
import com.company.crm.common.response.Response;
import com.company.crm.communication.integration.TwilioClient;
import com.company.crm.integration.health.IntegrationHealthService;
import com.company.crm.integration.repository.IntegrationApiKeyRepository;
import com.company.crm.notification.push.PushSender;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * NAV-M11: Settings → Integrations for the tenant admin — which outside services this CRM is set
 * up with (email, SMS, WhatsApp, push, AI, payments, ERP) and whether any has a credential problem
 * (INT-8). The credentials themselves are deployment settings and are never shown.
 */
@RestController
@RequestMapping("/api/integrations/status")
@RequiredArgsConstructor
public class IntegrationStatusController {

    private final TwilioClient twilioClient;
    private final PushSender pushSender;
    private final LlmGateway llm;
    private final PaymentProperties paymentProperties;
    private final IntegrationHealthService health;
    private final IntegrationApiKeyRepository apiKeys;

    @Value("${spring.mail.host:}")
    private String mailHost;

    public record IntegrationStatus(String key, String label, boolean configured, String detail, String problem) {}

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "NAV-M11: which integrations are set up, and any credential problem")
    public Response<List<IntegrationStatus>> status(@AuthenticationPrincipal User currentUser) {
        long activeKeys = apiKeys.findByTenantIdOrderByCreatedAtDesc(currentUser.getTenant().getId()).stream()
                .filter(k -> k.getRevokedAt() == null).count();
        return Response.ok(List.of(
                new IntegrationStatus("email", "Email", !mailHost.isBlank(), "Quotations, reminders, campaigns", problem(IntegrationHealthService.SMTP)),
                new IntegrationStatus("sms", "SMS", twilioClient.isSmsEnabled(), "Alerts and SMS campaigns", problem(IntegrationHealthService.TWILIO)),
                new IntegrationStatus("whatsapp", "WhatsApp", twilioClient.isWhatsAppEnabled(), "Customer updates and WhatsApp campaigns",
                        problem(IntegrationHealthService.TWILIO)),
                new IntegrationStatus("push", "Mobile push", pushSender.isEnabled(), "Notifications on the mobile app", problem(IntegrationHealthService.FCM)),
                new IntegrationStatus("ai", "AI assistant", llm.isConfigured(), "Chat, drafting, sentiment", problem(IntegrationHealthService.ANTHROPIC)),
                new IntegrationStatus("payments", "Online payments", paymentProperties.keyId() != null && !paymentProperties.keyId().isBlank(),
                        "Subscription payments", problem(IntegrationHealthService.RAZORPAY)),
                new IntegrationStatus("erp", "ERP sync", activeKeys > 0, activeKeys + " active API key(s)", null)));
    }

    private String problem(String integration) {
        return health.problem(integration).orElse(null);
    }
}
