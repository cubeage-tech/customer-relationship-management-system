package com.company.crm.common.exception;

import org.springframework.http.HttpStatus;

/** The tenant's subscription has lapsed — reads still work, writes need a renewal. */
public class SubscriptionInactiveException extends ApiException {

    public static final String CODE = "SUBSCRIPTION_INACTIVE";

    public SubscriptionInactiveException(String message) {
        super(HttpStatus.PAYMENT_REQUIRED, message, CODE);
    }
}
