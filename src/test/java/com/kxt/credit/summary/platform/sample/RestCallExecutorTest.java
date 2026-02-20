package com.kxt.credit.summary.platform.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.cfbl.platform.core.exception.api.ApiResponse;
import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.ErrorCode;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RestCallExecutorTest {

    private final RestCallExecutor executor = new RestCallExecutor();

    @Test
    void shouldReturnSuccessApiResponseOn200() {
        WebClient client = clientReturning(
            ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                .body("ok-response")
                .build()
        );

        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        Mono<ApiResponse<String>> result = executor.execute(
            holder,
            HttpMethod.GET,
            "fetchSample",
            "/sample",
            c -> c.get().uri("/sample"),
            String.class,
            "GET failed"
        );

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.status()).isEqualTo(200);
                assertThat(response.data()).isEqualTo("ok-response");
                assertThat(response.metadata()).isNotNull();
                assertThat(response.metadata().serviceId()).isEqualTo("sample-api");
                assertThat(response.metadata().resolvedEndpoint()).isEqualTo("https://example.com/sample");
                assertThat(response.metadata().protocolMeta().get("operation")).isEqualTo("fetchSample");
            })
            .verifyComplete();
    }

    @Test
    void shouldPreserveCreatedStatusInSuccessResponse() {
        WebClient client = clientReturning(
            ClientResponse.create(HttpStatus.CREATED)
                .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                .body("created-response")
                .build()
        );

        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        Mono<ApiResponse<String>> result = executor.execute(
            holder,
            HttpMethod.POST,
            "createSample",
            "/sample",
            c -> c.post().uri("/sample").bodyValue("{}"),
            String.class,
            "POST failed"
        );

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.status()).isEqualTo(201);
                assertThat(response.data()).isEqualTo("created-response");
            })
            .verifyComplete();
    }

    @Test
    void shouldReturnSingleSuccessResponseForNoContent() {
        WebClient client = clientReturning(
            ClientResponse.create(HttpStatus.NO_CONTENT).build()
        );

        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        Mono<ApiResponse<String>> result = executor.execute(
            holder,
            HttpMethod.GET,
            "fetchNoContent",
            "/sample",
            c -> c.get().uri("/sample"),
            String.class,
            "GET failed"
        );

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.status()).isEqualTo(204);
                assertThat(response.data()).isNull();
                assertThat(response.metadata()).isNotNull();
            })
            .verifyComplete();
    }

    @Test
    void shouldMapNon200ToDataCollectionException() {
        WebClient client = clientReturning(
            ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                .body("downstream unavailable")
                .build()
        );

        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        Mono<ApiResponse<String>> result = executor.execute(
            holder,
            HttpMethod.GET,
            "fetchSample",
            "/sample",
            c -> c.get().uri("/sample"),
            String.class,
            "GET failed"
        );

        StepVerifier.create(result)
            .expectErrorSatisfies(ex -> {
                assertThat(ex).isInstanceOf(CreditSummaryDataCollectionException.class);
                CreditSummaryDataCollectionException cse = (CreditSummaryDataCollectionException) ex;
                assertThat(cse.getCode()).isEqualTo(ErrorCode.DATA_COLLECTION_LAYER_EXCEPTION);
                assertThat(cse.getMessage()).contains("HTTP 503");
                assertThat(cse.getUpstream()).isNotNull();
                assertThat(cse.getUpstream().httpStatus()).isEqualTo(503);
                assertThat(cse.getSource()).isNotNull();
                assertThat(cse.getSource().resolvedEndpoint()).isEqualTo("https://example.com/sample");
            })
            .verify();
    }

    @Test
    void shouldWrapTimeoutExceptionAsDataCollectionException() {
        WebClient client = clientFailing(new TimeoutException("timed out"));
        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        Mono<ApiResponse<String>> result = executor.execute(
            holder,
            HttpMethod.POST,
            "createSample",
            "/sample",
            c -> c.post().uri("/sample").bodyValue("{}"),
            String.class,
            "POST failed"
        );

        StepVerifier.create(result)
            .expectErrorSatisfies(ex -> {
                assertThat(ex).isInstanceOf(CreditSummaryDataCollectionException.class);
                CreditSummaryDataCollectionException cse = (CreditSummaryDataCollectionException) ex;
                assertThat(cse.getCode()).isEqualTo(ErrorCode.DATA_COLLECTION_LAYER_EXCEPTION);
                assertThat(cse.getMessage()).isEqualTo("POST failed");
                assertThat(cse.getCause()).isInstanceOf(TimeoutException.class);
            })
            .verify();
    }

    @Test
    void shouldPassThroughExistingPlatformException() {
        CreditSummaryPlatformException original = new CreditSummaryDataCollectionException(
            ErrorCode.DATA_COLLECTION_LAYER_EXCEPTION,
            "already mapped"
        );

        WebClient client = clientFailing(original);
        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        Mono<ApiResponse<String>> result = executor.execute(
            holder,
            HttpMethod.GET,
            "fetchSample",
            "/sample",
            c -> c.get().uri("/sample"),
            String.class,
            "GET failed"
        );

        StepVerifier.create(result)
            .expectErrorSatisfies(ex -> assertThat(ex).isSameAs(original))
            .verify();
    }

    private WebClient clientReturning(ClientResponse response) {
        return WebClient.builder()
            .baseUrl("https://example.com")
            .exchangeFunction(request -> Mono.just(response))
            .build();
    }

    private WebClient clientFailing(Throwable throwable) {
        return WebClient.builder()
            .baseUrl("https://example.com")
            .exchangeFunction(request -> Mono.error(throwable))
            .build();
    }
}
