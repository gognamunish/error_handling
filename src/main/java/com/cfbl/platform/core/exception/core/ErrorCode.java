package com.cfbl.platform.core.exception.core;

import org.springframework.http.HttpStatus;

/**
 * Canonical error codes used by the API error envelope.
 */
public enum ErrorCode {
    DATA_COLLECTION_LAYER_EXCEPTION(HttpStatus.BAD_GATEWAY, "Data collection layer exception"),
    BUSINESS_LAYER_EXCEPTION(HttpStatus.BAD_REQUEST, "Business layer exception"),
    PRESENTATION_LAYER_EXCEPTION(HttpStatus.BAD_REQUEST, "Presentation layer exception"),
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
