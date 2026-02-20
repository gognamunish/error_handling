package com.cfbl.platform.core.exception.api;

import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central exception handler for the Credit Summary API.
 *
 * <p>All handled exceptions are translated into a consistent {@link ApiResponse} error envelope.
 */
@RestControllerAdvice
public class ApiGlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiGlobalExceptionHandler.class);

    private final ApiResponseMapper apiResponseMapper;

    public ApiGlobalExceptionHandler(ApiResponseMapper apiResponseMapper) {
        this.apiResponseMapper = apiResponseMapper;
    }

    /**
     * Handles domain-level platform exceptions and preserves their mapped HTTP status.
     */
    @ExceptionHandler(CreditSummaryPlatformException.class)
    public ResponseEntity<ApiResponse<Void>> handleCreditSummaryPlatformException(CreditSummaryPlatformException ex) {
        ApiResponse<Void> body = apiResponseMapper.fromPlatformException(ex);
        return ResponseEntity.status(ex.getCode().httpStatus()).body(body);
    }

    /**
     * Handles missing-route errors from Spring MVC.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        ApiResponse<Void> body = apiResponseMapper.fromCode(ErrorCode.NOT_FOUND, ex.getMessage());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.httpStatus()).body(body);
    }

    /**
     * Handles unsupported HTTP method errors from Spring MVC.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ApiResponse<Void> body = apiResponseMapper.fromCode(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.httpStatus()).body(body);
    }

    /**
     * Handles uncaught errors with a sanitized internal-error response.
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiResponse<Void>> handleThrowable(Throwable ex) {
        LOG.error("Unhandled exception", ex);
        ApiResponse<Void> body = apiResponseMapper.fromCode(
            ErrorCode.INTERNAL_ERROR,
            ErrorCode.INTERNAL_ERROR.defaultMessage()
        );
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus()).body(body);
    }
}
