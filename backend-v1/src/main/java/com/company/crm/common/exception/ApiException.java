package com.company.crm.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Business/validation failure that should be surfaced to the client with a specific HTTP status. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    /** Optional machine-readable code the frontend can branch on; null for generic errors. */
    private final String errorCode;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
