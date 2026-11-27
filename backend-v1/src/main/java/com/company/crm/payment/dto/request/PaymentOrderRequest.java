package com.company.crm.payment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentOrderRequest(
        /** Plan code: starter | business | enterprise */
        @NotBlank String plan,
        /** monthly | annual */
        @NotBlank String billingCycle
) {
}
