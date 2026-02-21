package com.cfbl.platform.core.executor;

import static org.assertj.core.api.Assertions.assertThat;

import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.integration.model.ProviderResult;
import com.cfbl.platform.core.retry.RetryPolicyExecutor;
import com.cfbl.platform.core.retry.RetrySettings;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final RestCallExecutor executor = new RestCallExecutor(new RetryPolicyExecutor());
    private final RestCallExecutor retryingExecutor = new RestCallExecutor(
        new RetryPolicyExecutor(new RetrySettings(true, 3, 1))
    );

    @Test
    void shouldReturnSuccessApiResponseOn200() {
        WebClient client = clientReturning(
            ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                .body("ok-response")
                .build()
        );

        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        Mono<ProviderResult<String>> result = executor.executeProvider(
            holder,
            HttpMethod.GET,
            "fetchSample",
            "/sample",
            () -> holder.webClient().get().uri("/sample"),
            String.class,
            "GET failed"
        );

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.status()).isEqualTo(200);
                assertThat(response.data()).isEqualTo("ok-response");
                assertThat(response.metadata()).isNotNull();
                assertThat(response.metadata().serviceId()).isEqualTo("sample-api");
                assertThat(response.metadata().endpoint()).isEqualTo("https://example.com/sample");
                assertThat(response.metadata().protocolAttributes().get("operation")).isEqualTo("fetchSample");
                assertThat(response.retry()).isNotNull();
                assertThat(response.retry().attempted()).isEqualTo(1);
                assertThat(response.retry().retried()).isFalse();
                assertThat(response.retry().exhausted()).isFalse();
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

        Mono<ProviderResult<String>> result = executor.executeProvider(
            holder,
            HttpMethod.POST,
            "createSample",
            "/sample",
            () -> holder.webClient().post().uri("/sample").bodyValue("{}"),
            String.class,
            "POST failed"
        );

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.status()).isEqualTo(201);
                assertThat(response.data()).isEqualTo("created-response");
                assertThat(response.retry()).isNotNull();
                assertThat(response.retry().attempted()).isEqualTo(1);
            })
            .verifyComplete();
    }

    @Test
    void shouldReturnSingleSuccessResponseForNoContent() {
        WebClient client = clientReturning(
            ClientResponse.create(HttpStatus.NO_CONTENT).build()
        );

        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        Mono<ProviderResult<String>> result = executor.executeProvider(
            holder,
            HttpMethod.GET,
            "fetchNoContent",
            "/sample",
            () -> holder.webClient().get().uri("/sample"),
            String.class,
            "GET failed"
        );

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.status()).isEqualTo(204);
                assertThat(response.data()).isNull();
                assertThat(response.metadata()).isNotNull();
                assertThat(response.retry()).isNotNull();
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

        Mono<ProviderResult<String>> result = executor.executeProvider(
            holder,
            HttpMethod.GET,
            "fetchSample",
            "/sample",
            () -> holder.webClient().get().uri("/sample"),
            String.class,
            "GET failed"
        );

        StepVerifier.create(result)
            .expectErrorSatisfies(ex -> {
                assertThat(ex).isInstanceOf(CreditSummaryDataCollectionException.class);
                CreditSummaryDataCollectionException cse = (CreditSummaryDataCollectionException) ex;
                assertThat(cse.getCode()).isEqualTo(ErrorCode.LAYER_DATA_COLLECTION_FAILURE);
                assertThat(cse.getMessage()).contains("HTTP 503");
                assertThat(cse.getUpstream()).isNotNull();
                assertThat(cse.getUpstream().httpStatus()).isEqualTo(503);
                assertThat(cse.getProviderContext()).isNotNull();
                assertThat(cse.getProviderContext().endpoint()).isEqualTo("https://example.com/sample");
                assertThat(cse.getRetryInfo()).isNotNull();
                assertThat(cse.getRetryInfo().attempted()).isEqualTo(3);
                assertThat(cse.getRetryInfo().exhausted()).isTrue();
            })
            .verify();
    }

    @Test
    void shouldNotRetryForNotFoundStatus() {
        WebClient client = clientReturning(
            ClientResponse.create(HttpStatus.NOT_FOUND)
                .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                .body("not found")
                .build()
        );

        WebClientHolder holder = new WebClientHolder(
            "sample-api",
            "https://example.com",
            client,
            new RetrySettings(true, 3, 1)
        );

        Mono<ProviderResult<String>> result = retryingExecutor.executeProvider(
            holder,
            HttpMethod.GET,
            "fetchMissing",
            "/sample",
            () -> holder.webClient().get().uri("/sample"),
            String.class,
            "GET failed"
        );

        StepVerifier.create(result)
            .expectErrorSatisfies(ex -> {
                assertThat(ex).isInstanceOf(CreditSummaryDataCollectionException.class);
                CreditSummaryDataCollectionException cse = (CreditSummaryDataCollectionException) ex;
                assertThat(cse.getUpstream()).isNotNull();
                assertThat(cse.getUpstream().httpStatus()).isEqualTo(404);
                assertThat(cse.getRetryInfo()).isNotNull();
                assertThat(cse.getRetryInfo().attempted()).isEqualTo(1);
                assertThat(cse.getRetryInfo().retried()).isFalse();
                assertThat(cse.getRetryInfo().exhausted()).isFalse();
            })
            .verify();
    }

    @Test
    void shouldWrapTimeoutExceptionAsDataCollectionException() {
        WebClient client = clientFailing(new TimeoutException("timed out"));
        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        Mono<ProviderResult<String>> result = executor.executeProvider(
            holder,
            HttpMethod.POST,
            "createSample",
            "/sample",
            () -> holder.webClient().post().uri("/sample").bodyValue("{}"),
            String.class,
            "POST failed"
        );

        StepVerifier.create(result)
            .expectErrorSatisfies(ex -> {
                assertThat(ex).isInstanceOf(CreditSummaryDataCollectionException.class);
                CreditSummaryDataCollectionException cse = (CreditSummaryDataCollectionException) ex;
                assertThat(cse.getCode()).isEqualTo(ErrorCode.LAYER_DATA_COLLECTION_FAILURE);
                assertThat(cse.getMessage()).isEqualTo("POST failed");
                assertThat(cse.getCause()).isInstanceOf(TimeoutException.class);
                assertThat(cse.getRetryInfo()).isNotNull();
                assertThat(cse.getRetryInfo().attempted()).isEqualTo(3);
                assertThat(cse.getRetryInfo().exhausted()).isTrue();
            })
            .verify();
    }

    @Test
    void shouldPassThroughExistingPlatformException() {
        CreditSummaryPlatformException original = new CreditSummaryDataCollectionException(
            ErrorCode.LAYER_DATA_COLLECTION_FAILURE,
            "already mapped"
        );

        WebClient client = clientFailing(original);
        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        Mono<ProviderResult<String>> result = executor.executeProvider(
            holder,
            HttpMethod.GET,
            "fetchSample",
            "/sample",
            () -> holder.webClient().get().uri("/sample"),
            String.class,
            "GET failed"
        );

        StepVerifier.create(result)
            .expectErrorSatisfies(ex -> assertThat(ex).isSameAs(original))
            .verify();
    }

    @Test
    void shouldRetryAndSucceedOnThirdAttempt() {
        AtomicInteger attempts = new AtomicInteger();
        WebClient client = WebClient.builder()
            .baseUrl("https://example.com")
            .exchangeFunction(request -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
                }
                return Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                        .body("ok-after-retry")
                        .build()
                );
            })
            .build();

        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        Mono<ProviderResult<String>> result = retryingExecutor.executeProvider(
            holder,
            HttpMethod.GET,
            "retryThenSuccess",
            "/sample",
            () -> holder.webClient().get().uri("/sample"),
            String.class,
            "GET failed"
        );

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.data()).isEqualTo("ok-after-retry");
                assertThat(response.retry()).isNotNull();
                assertThat(response.retry().attempted()).isEqualTo(3);
                assertThat(response.retry().retried()).isTrue();
                assertThat(response.retry().exhausted()).isFalse();
            })
            .verifyComplete();
    }

    @Test
    void shouldRetryUsingCallerProvidedPredicate() {
        AtomicInteger attempts = new AtomicInteger();
        WebClient client = clientFailing(new IllegalStateException("custom transient"));
        WebClientHolder holder = new WebClientHolder(
            "sample-api",
            "https://example.com",
            client,
            new RetrySettings(true, 3, 1)
        );

        Mono<ProviderResult<String>> result = retryingExecutor.executeProvider(
            holder,
            HttpMethod.GET,
            "customRetry",
            "/sample",
            () -> {
                attempts.incrementAndGet();
                return holder.webClient().get().uri("/sample");
            },
            String.class,
            "GET failed",
            ex -> ex instanceof IllegalStateException
        );

        StepVerifier.create(result)
            .expectErrorSatisfies(ex -> {
                assertThat(ex).isInstanceOf(CreditSummaryDataCollectionException.class);
                CreditSummaryDataCollectionException cse = (CreditSummaryDataCollectionException) ex;
                assertThat(cse.getRetryInfo()).isNotNull();
                assertThat(cse.getRetryInfo().attempted()).isEqualTo(3);
                assertThat(cse.getRetryInfo().retried()).isTrue();
                assertThat(cse.getRetryInfo().exhausted()).isTrue();
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
