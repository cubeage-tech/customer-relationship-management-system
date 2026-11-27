package com.company.crm.common.security;

import com.company.crm.common.response.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * API-4: 401 and 403 decided by the security filters use the same JSON envelope as every other
 * error (message, errorCode, requestId), instead of an empty body.
 */
final class JsonSecurityErrors {

    private JsonSecurityErrors() {
    }

    static AuthenticationEntryPoint unauthorized(ObjectMapper mapper) {
        return (request, response, e) -> write(response, mapper, HttpStatus.UNAUTHORIZED, "Sign in to continue", "UNAUTHENTICATED");
    }

    static AccessDeniedHandler forbidden(ObjectMapper mapper) {
        return (request, response, e) -> write(response, mapper, HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action", "FORBIDDEN");
    }

    private static void write(HttpServletResponse response, ObjectMapper mapper, HttpStatus status, String message, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), Response.error(message, code));
    }
}
