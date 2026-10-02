package com.company.crm.common.exception;

import org.springframework.http.HttpStatus;

/** Creating another record would take the tenant past its plan's limit. */
public class PlanLimitExceededException extends ApiException {

    public static final String CODE = "PLAN_LIMIT_EXCEEDED";

    public PlanLimitExceededException(String message) {
        super(HttpStatus.FORBIDDEN, message, CODE);
    }
}
