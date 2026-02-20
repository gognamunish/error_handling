package com.cfbl.platform.core.exception.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErrorCodeTest {

    @ParameterizedTest
    @CsvSource({
        "DATA_COLLECTION_LAYER_EXCEPTION,502",
        "BUSINESS_LAYER_EXCEPTION,400",
        "PRESENTATION_LAYER_EXCEPTION,400",
        "INTERNAL_ERROR,500",
        "NOT_FOUND,404",
        "METHOD_NOT_ALLOWED,405"
    })
    void shouldMapErrorCodeToHttpStatus(ErrorCode code, int expected) {
        assertThat(code.httpStatus().value()).isEqualTo(expected);
    }
}
