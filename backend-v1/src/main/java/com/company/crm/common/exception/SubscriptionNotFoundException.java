package com.company.crm.common.exception;

import org.springframework.http.HttpStatus;

/** The tenant has no subscription row at all (e.g. its admin never verified their email). */
public class SubscriptionNotFoundException extends ApiException {

    public static final String CODE = "SUBSCRIPTION_NOT_FOUND";

    public SubscriptionNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message, CODE);
    }
}
