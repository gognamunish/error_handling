package com.kxt.credit.summary.platform.sample;

import com.cfbl.platform.core.exception.api.ApiResponse;
import com.cfbl.platform.core.exception.core.CreditSummaryBusinessException;
import com.cfbl.platform.core.exception.core.ErrorCode;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
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

    public ExampleWebClientService(WebClient webClient) {
        this.restCallExecutor = new RestCallExecutor();
        this.sampleApi = new WebClientHolder(
            SERVICE_ID,
            SAMPLE_API_ENDPOINT,
            webClient.mutate().baseUrl(SAMPLE_API_ENDPOINT).build()
        );
    }

    /**
     * Example GET call returning raw string payload.
     */
    public Mono<ApiResponse<String>> fetchSampleWithContext() {
        return restCallExecutor.execute(
            sampleApi,
            HttpMethod.GET,
            "fetchSampleWithContext",
            SAMPLE_PATH,
            client -> client.get().uri(SAMPLE_PATH),
            String.class,
            "Sample API GET failed"
        );
    }

    /**
     * Example POST call returning raw string payload.
     */
    public Mono<ApiResponse<String>> createSampleWithContext(String customerId) {
        if (isBlank(customerId)) {
            return Mono.error(new CreditSummaryBusinessException(
                ErrorCode.BUSINESS_LAYER_EXCEPTION,
                "customerId is required"
            ));
        }

        return restCallExecutor.execute(
            sampleApi,
            HttpMethod.POST,
            "createSampleWithContext",
            SAMPLE_PATH,
            client -> client.post().uri(SAMPLE_PATH).bodyValue(new SampleCreateRequest(customerId)),
            String.class,
            "Sample API POST failed"
        );
    }

    /**
     * Example GET call returning a typed DTO.
     */
    public Mono<ApiResponse<SampleA>> fetchSampleAsObjectA() {
        return restCallExecutor.execute(
            sampleApi,
            HttpMethod.GET,
            "fetchSampleAsObjectA",
            SAMPLE_PATH,
            client -> client.get().uri(SAMPLE_PATH),
            SampleA.class,
            "Sample API GET for SampleA failed"
        );
    }

    /**
     * Example GET call returning an alternate typed DTO.
     */
    public Mono<ApiResponse<SampleB>> fetchSampleAsObjectB() {
        return restCallExecutor.execute(
            sampleApi,
            HttpMethod.GET,
            "fetchSampleAsObjectB",
            SAMPLE_PATH,
            client -> client.get().uri(SAMPLE_PATH),
            SampleB.class,
            "Sample API GET for SampleB failed"
        );
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
