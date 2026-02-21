package com.cfbl.platform.core.exception.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.retry.RetryInfo;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

/**
 * Unified API envelope for both success and error responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    Instant timestamp,
    String traceId,
    int status,
    T data,
    DataProviderContext metadata,
    RetryInfo retry,
    Error error
) {

    /**
     * Builds a successful API response with payload and metadata.
     */
    public static <T> ApiResponse<T> success(T data, DataProviderContext metadata) {
        return success(data, HttpStatus.OK.value(), metadata, null);
    }

    /**
     * Builds a successful API response with payload, metadata and explicit status.
     */
    public static <T> ApiResponse<T> success(T data, int status, DataProviderContext metadata) {
        return success(data, status, metadata, null);
    }

    /**
     * Builds a successful API response with payload, metadata, status and retry details.
     */
    public static <T> ApiResponse<T> success(T data, int status, DataProviderContext metadata, RetryInfo retry) {
        return new ApiResponse<>(
            Instant.now(),
            resolveTraceId(),
            status,
            data,
            metadata,
            retry,
            null
        );
    }

    public static ApiResponse<Void> error(
        HttpStatus status,
        String code,
        String layer,
        String reason,
        String message,
        DataProviderContext metadata,
        RetryInfo retry,
        Upstream upstream,
        List<ValidationError> validationErrors
    ) {
        return new ApiResponse<>(
            Instant.now(),
            resolveTraceId(),
            status.value(),
            null,
            metadata,
            retry,
            new Error(code, layer, reason, message, upstream, validationErrors)
        );
    }

    private static String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? "N/A" : traceId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    /**
     * Error payload details included when a request fails.
     */
    public record Error(
        String code,
        String layer,
        String reason,
        String message,
        Upstream upstream,
        List<ValidationError> validationErrors
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    /**
     * Raw upstream failure details when available.
     */
    public record Upstream(
        Integer httpStatus,
        String rawMessage,
        Long responseTimeMs
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    /**
     * Field-level validation errors.
     */
    public record ValidationError(
        String field,
        String message
    ) {
    }
}
