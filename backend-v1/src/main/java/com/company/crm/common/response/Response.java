package com.company.crm.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/** Standard API response envelope used by all controllers. */
@Getter
public class Response<T> {

    private final boolean success;
    private final String message;
    private final T data;

    /** Machine-readable error code (e.g. PLAN_LIMIT_EXCEEDED) — only present on some errors. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String errorCode;

    private Response(boolean success, String message, T data, String errorCode) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errorCode = errorCode;
    }

    public static <T> Response<T> ok(T data) {
        return new Response<>(true, null, data, null);
    }

    public static <T> Response<T> ok(String message, T data) {
        return new Response<>(true, message, data, null);
    }

    public static <T> Response<T> error(String message) {
        return new Response<>(false, message, null, null);
    }

    public static <T> Response<T> error(String message, String errorCode) {
        return new Response<>(false, message, null, errorCode);
    }
}
