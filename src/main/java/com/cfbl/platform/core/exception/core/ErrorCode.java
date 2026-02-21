package com.cfbl.platform.core.exception.core;

import org.springframework.http.HttpStatus;

/**
 * Canonical error codes used by the API error envelope.
 */
public enum ErrorCode {
    LAYER_DATA_COLLECTION_FAILURE(HttpStatus.BAD_GATEWAY, "Data collection layer failure"),
    LAYER_BUSINESS_FAILURE(HttpStatus.BAD_REQUEST, "Business layer failure"),
    LAYER_PRESENTATION_FAILURE(HttpStatus.BAD_REQUEST, "Presentation layer failure"),
    PLATFORM_EXECUTION_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "Platform execution failure"),
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
