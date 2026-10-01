package com.company.crm.common.exception;

import com.company.crm.common.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error leaves as the standard {@link Response} envelope:
 * {@code {"success": false, "message": "...", "errorCode": "..."}} (errorCode only when set).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * All business errors, including the typed subclasses — SubscriptionInactiveException (402),
     * PlanLimitExceededException (403), TenantInactiveException (403), InvalidTokenException (400),
     * TokenExpiredException (410), TenantNotFoundException (404), PaymentGatewayException (502),
     * SubscriptionNotFoundException (404), InvalidPlanChangeException (400) —
     * each of which carries its own status and errorCode.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Response<Object>> handleApiException(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("{}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        }
        return ResponseEntity.status(ex.getStatus()).body(Response.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler({ BadCredentialsException.class })
    public ResponseEntity<Response<Object>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Response.error("Invalid email or password"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Response<Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Response.error("You do not have permission to perform this action"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(Response.error("Validation failed: " + fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response<Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Response.error("Malformed or missing request body"));
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class
    })
    public ResponseEntity<Response<Object>> handleBadRequestParameters(Exception ex) {
        return ResponseEntity.badRequest().body(Response.error("Invalid request: " + ex.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Response<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Response.error(ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Response<Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Response.error("Not found"));
    }

    @ExceptionHandler(java.lang.Exception.class)
    public ResponseEntity<Response<Object>> handleUnexpected(java.lang.Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Response.error("Something went wrong. Please try again."));
    }
}
