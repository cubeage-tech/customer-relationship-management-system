package com.company.crm.common.exception;

import org.springframework.http.HttpStatus;

/** The user's company account (tenant) has been suspended or deactivated by the platform. */
public class TenantInactiveException extends ApiException {

    public static final String CODE = "TENANT_INACTIVE";

    public TenantInactiveException(String message) {
        super(HttpStatus.FORBIDDEN, message, CODE);
    }
}
