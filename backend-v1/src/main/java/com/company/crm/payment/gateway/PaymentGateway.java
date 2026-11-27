package com.company.crm.payment.gateway;

import java.util.Optional;

/**
 * Provider-neutral payment operations. PaymentService only talks to this interface, so
 * swapping Razorpay for another provider means adding one implementation and changing
 * {@code payment.gateway.provider}.
 */
public interface PaymentGateway {

    /** Stored on each Payment row, e.g. "razorpay". */
    String provider();

    /** Public key the browser checkout widget needs. Never the secret. */
    String checkoutKey();

    /** Creates an order for {@code amountInMinorUnits} (paise/cents) with the provider. */
    GatewayOrder createOrder(long amountInMinorUnits, String currency, String receipt);

    /** Checks the signature the checkout widget returned after a payment. */
    boolean isPaymentSignatureValid(String orderId, String paymentId, String signature);

    /**
     * Verifies a webhook's signature and extracts the payment outcome.
     * Empty for events this app doesn't act on.
     *
     * @throws com.company.crm.common.exception.ApiException if the signature is invalid
     */
    Optional<WebhookEvent> parseWebhook(String payload, String signature);

    record GatewayOrder(String orderId, String currency, long amountInMinorUnits) {}

    record WebhookEvent(Type type, String orderId, String paymentId, String failureReason) {
        public enum Type { PAYMENT_CAPTURED, PAYMENT_FAILED }
    }
}
