package com.cfbl.platform.core.exception.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;

class ApiGlobalExceptionHandlerTest {

    private final ApiResponseMapper apiResponseMapper = new ApiResponseMapper();
    private final ApiGlobalExceptionHandler handler = new ApiGlobalExceptionHandler(apiResponseMapper);

    @Test
    void shouldReturnMappedPlatformError() {
        CreditSummaryPlatformException ex = new CreditSummaryPlatformException(
            ErrorCode.DATA_COLLECTION_LAYER_EXCEPTION,
            "upstream failed"
        );

        var response = handler.handleCreditSummaryPlatformException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("DATA_COLLECTION_LAYER_EXCEPTION");
        assertThat(response.getBody().error().module()).isEqualTo("CREDIT_SUMMARY_PLATFORM");
    }

    @Test
    void shouldReturnMethodNotAllowed() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");

        var response = handler.handleMethodNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getBody().error().module()).isEqualTo("CREDIT_SUMMARY_PLATFORM");
    }

    @Test
    void shouldReturnInternalErrorForUnhandled() {
        var response = handler.handleThrowable(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().error().module()).isEqualTo("CREDIT_SUMMARY_PLATFORM");
    }
}
