package com.company.crm.payment.gateway;

import com.company.crm.common.config.PaymentConfig.PaymentProperties;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.exception.PaymentGatewayException;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "razorpay", matchIfMissing = true)
@RequiredArgsConstructor
public class RazorpayPaymentGateway implements PaymentGateway {

    private final PaymentProperties paymentProperties;

    @Override
    public String provider() {
        return "razorpay";
    }

    @Override
    public String checkoutKey() {
        return paymentProperties.keyId();
    }

    @Override
    public GatewayOrder createOrder(long amountInMinorUnits, String currency, String receipt) {
        requireApiKeys();

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInMinorUnits);
        orderRequest.put("currency", currency);
        orderRequest.put("receipt", receipt);

        try {
            RazorpayClient client = new RazorpayClient(paymentProperties.keyId(), paymentProperties.keySecret());
            Order order = client.orders.create(orderRequest);
            return new GatewayOrder(
                    order.get("id"),
                    order.get("currency"),
                    Long.parseLong(order.get("amount").toString()));
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("Could not create payment order: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isPaymentSignatureValid(String orderId, String paymentId, String signature) {
        requireApiKeys();
        try {
            return Utils.verifySignature(orderId + "|" + paymentId, signature, paymentProperties.keySecret());
        } catch (RazorpayException e) {
            return false;
        }
    }

    @Override
    public Optional<WebhookEvent> parseWebhook(String payload, String signature) {
        if (isBlank(paymentProperties.webhookSecret())) {
            throw new PaymentGatewayException("Payment webhook secret is not configured");
        }

        boolean valid;
        try {
            valid = signature != null
                    && Utils.verifyWebhookSignature(payload, signature, paymentProperties.webhookSecret());
        } catch (RazorpayException e) {
            valid = false;
        }
        if (!valid) {
            throw ApiException.badRequest("Invalid webhook signature");
        }

        try {
            JSONObject event = new JSONObject(payload);
            String eventName = event.optString("event");
            WebhookEvent.Type type = switch (eventName) {
                case "payment.captured", "order.paid" -> WebhookEvent.Type.PAYMENT_CAPTURED;
                case "payment.failed" -> WebhookEvent.Type.PAYMENT_FAILED;
                default -> null;
            };
            if (type == null) {
                return Optional.empty();
            }

            JSONObject payment = event.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
            return Optional.of(new WebhookEvent(
                    type,
                    payment.optString("order_id", null),
                    payment.optString("id", null),
                    payment.optString("error_description", null)));
        } catch (JSONException e) {
            throw ApiException.badRequest("Malformed webhook payload");
        }
    }

    private void requireApiKeys() {
        if (isBlank(paymentProperties.keyId()) || isBlank(paymentProperties.keySecret())) {
            throw new PaymentGatewayException("Payment gateway is not configured — set PAYMENT_KEY_ID and PAYMENT_KEY_SECRET");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
