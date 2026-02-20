package com.kxt.credit.summary.platform.exception.api;

import com.kxt.credit.summary.platform.exception.core.ErrorCode;
import com.kxt.credit.summary.platform.exception.core.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorResponseFactory errorResponseFactory;

    public GlobalExceptionHandler(ErrorResponseFactory errorResponseFactory) {
        this.errorResponseFactory = errorResponseFactory;
    }

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ErrorResponse> handlePlatformException(PlatformException ex) {
        ErrorResponse body = errorResponseFactory.fromPlatformException(ex);
        return ResponseEntity.status(ex.getCode().httpStatus()).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
        ErrorResponse body = errorResponseFactory.fromCode(ErrorCode.NOT_FOUND, ex.getMessage());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.httpStatus()).body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ErrorResponse body = errorResponseFactory.fromCode(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.httpStatus()).body(body);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ErrorResponse> handleThrowable(Throwable ex) {
        LOG.error("Unhandled exception", ex);
        ErrorResponse body = errorResponseFactory.fromCode(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus()).body(body);
    }
}
