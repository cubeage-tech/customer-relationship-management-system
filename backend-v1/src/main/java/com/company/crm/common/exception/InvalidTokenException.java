package com.company.crm.common.exception;

import org.springframework.http.HttpStatus;

/** An emailed token (verification, password reset) is unknown or has already been used. */
public class InvalidTokenException extends ApiException {

    public static final String CODE = "TOKEN_INVALID";

    public InvalidTokenException(String message) {
        super(HttpStatus.BAD_REQUEST, message, CODE);
    }
}
