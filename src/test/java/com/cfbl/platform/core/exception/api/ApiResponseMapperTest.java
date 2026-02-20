package com.cfbl.platform.core.exception.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.exception.core.ErrorCode;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiResponseMapperTest {

    @Test
    void shouldKeepResolvedEndpoint() {
        ApiResponseMapper mapper = new ApiResponseMapper();

        DataProviderContext source = new DataProviderContext(
            DataProviderContext.Protocol.REST,
            "credit-bureau",
            "https://api.creditbureau.com/v2/score",
            Map.of("operation", "getCreditScore"),
            123,
            Instant.now()
        );

        CreditSummaryPlatformException ex = new CreditSummaryPlatformException(
            ErrorCode.DATA_COLLECTION_LAYER_EXCEPTION,
            "Upstream failed",
            source,
            null,
            null
        );

        ApiResponse<Void> response = mapper.fromPlatformException(ex);

        assertThat(response.metadata()).isNotNull();
        assertThat(response.metadata().resolvedEndpoint()).isEqualTo("https://api.creditbureau.com/v2/score");
        assertThat(response.error()).isNotNull();
        assertThat(response.error().code()).isEqualTo("DATA_COLLECTION_LAYER_EXCEPTION");
        assertThat(response.error().module()).isEqualTo("CREDIT_SUMMARY_PLATFORM");
    }
}
