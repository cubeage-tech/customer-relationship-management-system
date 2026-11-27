package com.company.crm.integration.controller;

import com.company.crm.common.response.Response;
import com.company.crm.communication.controller.TwilioWebhookController;
import com.company.crm.integration.dto.IntegrationDtos.*;
import com.company.crm.integration.service.ErpSyncService;
import com.company.crm.integration.service.IntegrationKeyService;
import com.company.crm.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * API-G12 (SRS §10.2): the Integrations group. None of these use a user login:
 * <ul>
 *   <li>/payment — the payment provider's webhook, authenticated by its signature (same as /api/payments/webhook);</li>
 *   <li>/whatsapp-webhook — Twilio's incoming-message webhook, authenticated by X-Twilio-Signature
 *       (same as /api/communications/webhooks/twilio/inbound);</li>
 *   <li>/erp/** — the generic ERP sync, authenticated by the tenant's X-Api-Key.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
public class IntegrationsController {

    public static final String WHATSAPP_WEBHOOK_PATH = "/api/integrations/whatsapp-webhook";

    private final PaymentService paymentService;
    private final TwilioWebhookController twilioWebhooks;
    private final IntegrationKeyService keyService;
    private final ErpSyncService erpSyncService;

    @PostMapping("/payment")
    @Operation(summary = "API-G12: payment provider webhook (X-Razorpay-Signature)")
    public Response<Void> payment(@RequestBody String payload,
                                  @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleWebhook(payload, signature);
        return Response.ok("Webhook processed", null);
    }

    @PostMapping(value = "/whatsapp-webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "API-G12: incoming WhatsApp/SMS from Twilio (X-Twilio-Signature over this URL)")
    public ResponseEntity<String> whatsappWebhook(@RequestParam Map<String, String> params,
                                                  @RequestHeader(value = "X-Twilio-Signature", required = false) String signature) {
        return twilioWebhooks.receiveInbound(WHATSAPP_WEBHOOK_PATH, params, signature);
    }

    // ==================== ERP (X-Api-Key) ====================

    @GetMapping("/erp/customers")
    @Operation(summary = "API-G12: customers changed since a time (oldest first, at most 500)")
    public Response<List<ErpCustomer>> customers(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return Response.ok(erpSyncService.customers(keyService.authenticate(apiKey), since));
    }

    @GetMapping("/erp/won-deals")
    @Operation(summary = "API-G12: deals won since a time — for invoicing in the ERP")
    public Response<List<ErpWonDeal>> wonDeals(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return Response.ok(erpSyncService.wonDeals(keyService.authenticate(apiKey), since));
    }

    @GetMapping("/erp/quotations")
    @Operation(summary = "API-G12: quotations the customer approved since a time, with line items — for sales orders")
    public Response<List<ErpQuotation>> quotations(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return Response.ok(erpSyncService.approvedQuotations(keyService.authenticate(apiKey), since));
    }

    @PutMapping("/erp/customers/{customerId}/erp-code")
    @Operation(summary = "API-G12: record the ERP's code for a CRM customer")
    public Response<ErpCustomer> customerCode(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                              @PathVariable Long customerId, @Valid @RequestBody CustomerCodeReq dto) {
        return Response.ok(erpSyncService.setCustomerCode(keyService.authenticate(apiKey), customerId, dto.getErpCode()));
    }

    @PutMapping("/erp/products")
    @Operation(summary = "API-G12: push the ERP price list — products matched by erpCode, created if new")
    public Response<ProductSyncRes> products(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                             @Valid @RequestBody ProductSyncReq dto) {
        return Response.ok(erpSyncService.syncProducts(keyService.authenticate(apiKey), dto));
    }
}
