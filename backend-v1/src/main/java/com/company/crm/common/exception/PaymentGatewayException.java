package com.company.crm.common.exception;

import org.springframework.http.HttpStatus;

/** The upstream payment provider failed or is not configured. */
public class PaymentGatewayException extends ApiException {

    public static final String CODE = "PAYMENT_GATEWAY_ERROR";

    public PaymentGatewayException(String message) {
        super(HttpStatus.BAD_GATEWAY, message, CODE);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        this(message);
        initCause(cause);
    }
}
