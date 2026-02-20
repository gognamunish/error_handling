package com.kxt.credit.summary.platform.exception.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.kxt.credit.summary.platform.config.PlatformProperties;
import com.kxt.credit.summary.platform.exception.core.ErrorCode;
import com.kxt.credit.summary.platform.exception.core.PlatformException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;

class GlobalExceptionHandlerTest {

    private final PlatformProperties properties = new PlatformProperties();
    private final ErrorResponseFactory errorResponseFactory = new ErrorResponseFactory(properties);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(errorResponseFactory);

    @Test
    void shouldReturnMappedPlatformError() {
        PlatformException ex = new PlatformException(ErrorCode.UPSTREAM_BAD_GATEWAY, "bad gateway");

        var response = handler.handlePlatformException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UPSTREAM_BAD_GATEWAY");
    }

    @Test
    void shouldReturnMethodNotAllowed() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");

        var response = handler.handleMethodNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void shouldReturnInternalErrorForUnhandled() {
        var response = handler.handleThrowable(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }
}
