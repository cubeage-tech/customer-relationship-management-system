package com.company.crm.payment.dto.response;

public record PaymentOrderResponse(
        String orderId,
        String keyId,
        String currency,
        Long amount,
        String plan,
        String billingCycle
) {
}