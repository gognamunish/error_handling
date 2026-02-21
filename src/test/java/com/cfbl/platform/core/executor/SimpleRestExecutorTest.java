package com.cfbl.platform.core.executor;

import static org.assertj.core.api.Assertions.assertThat;

import com.cfbl.platform.core.exception.api.ApiResponse;
import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.ErrorCode;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SimpleRestExecutorTest {

    private final SimpleRestExecutor executor = new SimpleRestExecutor();

    @Test
    void shouldReturnSuccessApiResponseOn200() {
        WebClient client = clientReturning(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                        .body("ok-response")
                        .build());

        Mono<ApiResponse<String>> result = executor.execute(
                client,
                "simple-api",
                "https://example.com/sample",
                c -> c.get().uri("/sample"),
                String.class);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo(200);
                    assertThat(response.data()).isEqualTo("ok-response");
                    assertThat(response.metadata()).isNotNull();
                    assertThat(response.metadata().serviceId()).isEqualTo("simple-api");
                    assertThat(response.metadata().endpoint()).isEqualTo("https://example.com/sample");
                    assertThat(response.retry()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldMapNon2xxToDataCollectionException() {
        WebClient client = clientReturning(
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                        .body("downstream unavailable")
                        .build());

        Mono<ApiResponse<String>> result = executor.execute(
                client,
                "simple-api",
                "https://example.com/sample",
                c -> c.get().uri("/sample"),
                String.class);

        StepVerifier.create(result)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(CreditSummaryDataCollectionException.class);
                    CreditSummaryDataCollectionException cse = (CreditSummaryDataCollectionException) ex;
                    assertThat(cse.getCode()).isEqualTo(ErrorCode.LAYER_DATA_COLLECTION_FAILURE);
                    assertThat(cse.getUpstream()).isNotNull();
                    assertThat(cse.getUpstream().httpStatus()).isEqualTo(503);
                })
                .verify();
    }

    @Test
    void shouldPassThroughExistingPlatformException() {
        CreditSummaryPlatformException original = new CreditSummaryDataCollectionException(
                ErrorCode.LAYER_DATA_COLLECTION_FAILURE,
                "already mapped");

        WebClient client = clientFailing(original);

        Mono<ApiResponse<String>> result = executor.execute(
                client,
                "simple-api",
                "https://example.com/sample",
                c -> c.get().uri("/sample"),
                String.class);

        StepVerifier.create(result)
                .expectErrorSatisfies(ex -> assertThat(ex).isSameAs(original))
                .verify();
    }

    @Test
    void shouldWrapUnexpectedException() {
        WebClient client = clientFailing(new TimeoutException("timed out"));

        Mono<ApiResponse<String>> result = executor.execute(
                client,
                "simple-api",
                "https://example.com/sample",
                "fetchSample",
                c -> c.get().uri("/sample"),
                String.class,
                "Simple call failed");

        StepVerifier.create(result)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(CreditSummaryDataCollectionException.class);
                    CreditSummaryDataCollectionException cse = (CreditSummaryDataCollectionException) ex;
                    assertThat(cse.getCode()).isEqualTo(ErrorCode.LAYER_DATA_COLLECTION_FAILURE);
                    assertThat(cse.getMessage()).isEqualTo("Simple call failed");
                    assertThat(cse.getCause()).isInstanceOf(TimeoutException.class);
                })
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
