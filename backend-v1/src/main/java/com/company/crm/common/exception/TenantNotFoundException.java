package com.company.crm.common.exception;

import org.springframework.http.HttpStatus;

public class TenantNotFoundException extends ApiException {

    public static final String CODE = "TENANT_NOT_FOUND";

    public TenantNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message, CODE);
    }
}
