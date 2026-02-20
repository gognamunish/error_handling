package com.kxt.credit.summary.platform.exception.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.kxt.credit.summary.platform.config.PlatformProperties;
import com.kxt.credit.summary.platform.exception.core.DataSourceContext;
import com.kxt.credit.summary.platform.exception.core.ErrorCode;
import com.kxt.credit.summary.platform.exception.core.PlatformException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorResponseFactoryTest {

    @Test
    void shouldMaskEndpointWhenConfigured() {
        PlatformProperties props = new PlatformProperties();
        props.setExposeEndpointInErrors(false);
        ErrorResponseFactory factory = new ErrorResponseFactory(props);

        DataSourceContext source = new DataSourceContext(
            DataSourceContext.Protocol.REST,
            "credit-bureau",
            "https://api.creditbureau.com/v2/score",
            Map.of("operation", "getCreditScore"),
            123,
            Instant.now()
        );

        PlatformException ex = new PlatformException(
            ErrorCode.UPSTREAM_BAD_GATEWAY,
            "Upstream failed",
            source,
            null,
            null
        );

        ErrorResponse response = factory.fromPlatformException(ex);

        assertThat(response.source()).isNotNull();
        assertThat(response.source().endpoint()).isEqualTo("[masked]");
    }
}
