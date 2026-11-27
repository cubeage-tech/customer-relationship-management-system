package com.company.crm.payment.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Field names match what Razorpay Checkout hands the browser after a successful payment. */
public record PaymentVerificationRequest(
        @NotBlank String razorpay_payment_id,
        @NotBlank String razorpay_order_id,
        @NotBlank String razorpay_signature
) {}
