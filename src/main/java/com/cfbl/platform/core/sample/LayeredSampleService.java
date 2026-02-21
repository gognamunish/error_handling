package com.cfbl.platform.core.sample;

import com.cfbl.platform.core.config.PlatformProperties;
import com.cfbl.platform.core.exception.core.CreditSummaryBusinessException;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.executor.RestCallExecutor;
import com.cfbl.platform.core.executor.WebClientHolder;
import com.cfbl.platform.core.executor.WebClientHolderFactory;
import com.cfbl.platform.core.integration.model.ProviderResult;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Layered sample service that returns integration-layer {@link ProviderResult}
 * values.
 */
@Service
public class LayeredSampleService {

    private static final String SERVICE_ID = "sample-api";
    private static final String SAMPLE_API_ENDPOINT = "https://example.com";
    private static final String SAMPLE_PATH = "/sample";

    private final RestCallExecutor restCallExecutor;
    private final WebClientHolder holder;

    public LayeredSampleService(
            RestCallExecutor restCallExecutor,
            WebClientHolderFactory holderFactory,
            PlatformProperties platformProperties) {
        this.restCallExecutor = restCallExecutor;
        String fallbackEndpoint = platformProperties.getServices().containsKey(SERVICE_ID)
                ? null
                : SAMPLE_API_ENDPOINT;
        this.holder = holderFactory.create(SERVICE_ID, fallbackEndpoint);
    }

    /**
     * Calls sample provider GET endpoint and returns provider-layer result.
     */
    public Mono<ProviderResult<String>> fetchSample() {
        return restCallExecutor.executeProvider(
                holder,
                HttpMethod.GET,
                "fetchSample",
                SAMPLE_PATH,
                () -> holder.webClient().get().uri(SAMPLE_PATH),
                "Sample API GET failed",
                throwable -> false);
    }

    /**
     * Calls sample provider POST endpoint and returns provider-layer result.
     */
    public Mono<ProviderResult<String>> createSample(String customerId) {
        if (isBlank(customerId)) {
            return Mono.error(new CreditSummaryBusinessException(
                    ErrorCode.LAYER_BUSINESS_FAILURE,
                    "customerId is required"));
        }

        return restCallExecutor.executeProvider(
                holder,
                HttpMethod.POST,
                "createSample",
                SAMPLE_PATH,
                () -> holder.webClient().post().uri(SAMPLE_PATH).bodyValue(new CreateSampleRequest(customerId)),
                "Sample API POST failed",
                throwable -> false);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CreateSampleRequest(String customerId) {
    }
}
