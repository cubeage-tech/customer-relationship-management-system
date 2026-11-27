package com.company.crm.common.exception;

import org.springframework.http.HttpStatus;

/** An emailed token (verification, password reset) is past its expiry — the user needs a new one. */
public class TokenExpiredException extends ApiException {

    public static final String CODE = "TOKEN_EXPIRED";

    public TokenExpiredException(String message) {
        super(HttpStatus.GONE, message, CODE);
    }
}
