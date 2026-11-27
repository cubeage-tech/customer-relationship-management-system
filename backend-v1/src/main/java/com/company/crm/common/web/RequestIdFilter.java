package com.company.crm.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * API-4: every request gets a correlation ID — the caller's X-Request-Id if it sent a sensible one
 * (e.g. from a gateway), else a new one. It is returned in the X-Request-Id header and in every
 * error body, and tags each log line of the request, so a reported error can be found in the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(RequestIds.HEADER);
        String id = incoming != null && ACCEPTED.matcher(incoming).matches() ? incoming : RequestIds.newId();
        MDC.put(RequestIds.MDC_KEY, id);
        response.setHeader(RequestIds.HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }
}
