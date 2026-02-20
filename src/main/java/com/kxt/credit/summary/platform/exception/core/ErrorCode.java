package com.kxt.credit.summary.platform.exception.core;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Upstream service unavailable"),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Upstream timeout"),
    UPSTREAM_BAD_GATEWAY(HttpStatus.BAD_GATEWAY, "Upstream gateway failure"),
    DATA_MAPPING_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, "Data mapping failure"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation error"),
    CONFIG_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Integration configuration error"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
