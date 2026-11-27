package com.company.crm.common.exception;

import org.springframework.http.HttpStatus;

/** The requested plan change isn't allowed, e.g. the target isn't a higher plan than the current one. */
public class InvalidPlanChangeException extends ApiException {

    public static final String CODE = "INVALID_PLAN_CHANGE";

    public InvalidPlanChangeException(String message) {
        super(HttpStatus.BAD_REQUEST, message, CODE);
    }
}
