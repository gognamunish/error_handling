package com.kxt.credit.summary.platform.exception.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErrorCodeTest {

    @ParameterizedTest
    @CsvSource({
        "UPSTREAM_UNAVAILABLE,503",
        "UPSTREAM_TIMEOUT,504",
        "UPSTREAM_BAD_GATEWAY,502",
        "DATA_MAPPING_ERROR,422",
        "VALIDATION_ERROR,400",
        "CONFIG_ERROR,500",
        "INTERNAL_ERROR,500",
        "NOT_FOUND,404",
        "METHOD_NOT_ALLOWED,405"
    })
    void shouldMapErrorCodeToHttpStatus(ErrorCode code, int expected) {
        assertThat(code.httpStatus().value()).isEqualTo(expected);
    }
}
