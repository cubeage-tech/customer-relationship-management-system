package com.company.crm.common.web;

import org.slf4j.MDC;

import java.util.UUID;

/** API-4: the current request's correlation ID (also in every log line of the request, as MDC "requestId"). */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestIds() {
    }

    /** The ID of the request being handled, or null outside a request (scheduled jobs). */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
