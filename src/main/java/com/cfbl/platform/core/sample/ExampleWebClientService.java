package com.cfbl.platform.core.sample;

import com.cfbl.platform.core.config.PlatformProperties;
import com.cfbl.platform.core.executor.RestCallExecutor;
import com.cfbl.platform.core.executor.WebClientHolder;
import com.cfbl.platform.core.executor.WebClientHolderFactory;
import com.cfbl.platform.core.exception.api.ApiResponse;
import com.cfbl.platform.core.exception.core.CreditSummaryBusinessException;
import com.cfbl.platform.core.exception.core.ErrorCode;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Sample service showing how to call provider APIs through {@link RestCallExecutor}.
 */
@Service
public class ExampleWebClientService {

    private static final String SERVICE_ID = "sample-api";
    private static final String SAMPLE_API_ENDPOINT = "https://example.com";
    private static final String SAMPLE_PATH = "/sample";

    private final RestCallExecutor restCallExecutor;
    private final WebClientHolder sampleApi;

    public ExampleWebClientService(
        RestCallExecutor restCallExecutor,
        WebClientHolderFactory holderFactory,
        PlatformProperties platformProperties
    ) {
        this.restCallExecutor = restCallExecutor;
        String fallbackEndpoint = platformProperties.getServices().containsKey(SERVICE_ID)
            ? null
            : SAMPLE_API_ENDPOINT;
        this.sampleApi = holderFactory.create(SERVICE_ID, fallbackEndpoint);
    }

    /**
     * Example GET call returning raw string payload.
     */
    public Mono<ApiResponse<String>> fetchSampleWithContext() {
        return restCallExecutor.executeProvider(
            sampleApi,
            HttpMethod.GET,
            "fetchSampleWithContext",
            SAMPLE_PATH,
            () -> sampleApi.webClient().get().uri(SAMPLE_PATH),
            String.class,
            "Sample API GET failed",
            throwable -> false
        ).map(r -> ApiResponse.success(r.data(), r.status(), r.metadata(), r.retry()));
    }

    /**
     * Example POST call returning raw string payload.
     */
    public Mono<ApiResponse<String>> createSampleWithContext(String customerId) {
        if (isBlank(customerId)) {
            return Mono.error(new CreditSummaryBusinessException(
                ErrorCode.LAYER_BUSINESS_FAILURE,
                "customerId is required"
            ));
        }

        return restCallExecutor.executeProvider(
            sampleApi,
            HttpMethod.POST,
            "createSampleWithContext",
            SAMPLE_PATH,
            () -> sampleApi.webClient().post().uri(SAMPLE_PATH).bodyValue(new SampleCreateRequest(customerId)),
            String.class,
            "Sample API POST failed",
            throwable -> false
        ).map(r -> ApiResponse.success(r.data(), r.status(), r.metadata(), r.retry()));
    }

    /**
     * Example GET call returning a typed DTO.
     */
    public Mono<ApiResponse<SampleA>> fetchSampleAsObjectA() {
        return restCallExecutor.executeProvider(
            sampleApi,
            HttpMethod.GET,
            "fetchSampleAsObjectA",
            SAMPLE_PATH,
            () -> sampleApi.webClient().get().uri(SAMPLE_PATH),
            SampleA.class,
            "Sample API GET for SampleA failed",
            throwable -> false
        ).map(r -> ApiResponse.success(r.data(), r.status(), r.metadata(), r.retry()));
    }

    /**
     * Example GET call returning an alternate typed DTO.
     */
    public Mono<ApiResponse<SampleB>> fetchSampleAsObjectB() {
        return restCallExecutor.executeProvider(
            sampleApi,
            HttpMethod.GET,
            "fetchSampleAsObjectB",
            SAMPLE_PATH,
            () -> sampleApi.webClient().get().uri(SAMPLE_PATH),
            SampleB.class,
            "Sample API GET for SampleB failed",
            throwable -> false
        ).map(r -> ApiResponse.success(r.data(), r.status(), r.metadata(), r.retry()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SampleCreateRequest(String customerId) {
    }

    public record SampleA(String id, String name) {
    }

    public record SampleB(String ref, Integer score) {
    }
}
