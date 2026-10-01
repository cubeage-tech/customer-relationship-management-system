package com.company.crm.payment.controller;

import com.company.crm.common.response.Response;
import com.company.crm.payment.dto.request.PaymentOrderRequest;
import com.company.crm.payment.dto.request.PaymentVerificationRequest;
import com.company.crm.payment.dto.response.PaymentOrderResponse;
import com.company.crm.payment.service.PaymentService;
import com.company.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** Only the tenant owner (admin) buys or renews the tenant's plan. */
    @PostMapping("/create-order")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Response<PaymentOrderResponse>> createOrder(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody PaymentOrderRequest request) {
        return ResponseEntity.ok(Response.ok(
                "Payment order created successfully",
                paymentService.createOrder(currentUser, request)));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Response<?>> verifyPayment(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody PaymentVerificationRequest request) {
        if (!paymentService.verifyPayment(currentUser, request)) {
            return ResponseEntity.badRequest().body(Response.error("Payment verification failed"));
        }
        return ResponseEntity.ok(Response.ok("Payment verified successfully", true));
    }

    /** Called by the payment provider, not the browser — authenticated by its signature header. */
    @PostMapping("/webhook")
    public Response<Void> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleWebhook(payload, signature);
        return Response.ok("Webhook processed", null);
    }
}
