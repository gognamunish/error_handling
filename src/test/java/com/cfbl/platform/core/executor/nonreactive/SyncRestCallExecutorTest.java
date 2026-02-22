package com.cfbl.platform.core.executor.nonreactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.executor.WebClientHolder;
import com.cfbl.platform.core.integration.model.ProviderResult;
import com.cfbl.platform.core.retry.RetrySettings;
import com.cfbl.platform.core.retry.SyncRetryPolicyExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class SyncRestCallExecutorTest {

    private final SyncRestCallExecutor executor = new SyncRestCallExecutor(new SyncRetryPolicyExecutor());
    private final SyncRestCallExecutor retryingExecutor = new SyncRestCallExecutor(
            new SyncRetryPolicyExecutor(new RetrySettings(true, 3, 1)));

    @Test
    void shouldReturnSuccessApiResponseOn200() {
        WebClient client = clientReturning(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                        .body("ok-response")
                        .build());

        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        ProviderResult<String> result = executor.executeWithRetry(
                holder,
                HttpMethod.GET,
                "fetchSample",
                "/sample",
                () -> holder.webClient().get().uri("/sample"),
                "GET failed");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.data()).isEqualTo("ok-response");
        assertThat(result.metadata()).isNotNull();
        assertThat(result.metadata().serviceId()).isEqualTo("sample-api");
        assertThat(result.metadata().endpoint()).isEqualTo("https://example.com/sample");
        assertThat(result.metadata().protocolAttributes().get("operation")).isEqualTo("fetchSample");
        assertThat(result.retry()).isNotNull();
        assertThat(result.retry().attempted()).isEqualTo(1);
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
                                    .build());
                })
                .build();

        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        ProviderResult<String> result = retryingExecutor.executeWithRetry(
                holder,
                HttpMethod.GET,
                "retryThenSuccess",
                "/sample",
                () -> holder.webClient().get().uri("/sample"),
                "GET failed");

        assertThat(result.data()).isEqualTo("ok-after-retry");
        assertThat(result.retry()).isNotNull();
        assertThat(result.retry().attempted()).isEqualTo(3);
        assertThat(result.retry().retried()).isTrue();
    }

    @Test
    void shouldMapExhaustedRetryToDataCollectionException() {
        WebClient client = clientReturning(
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("downstream down")
                        .build());

        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        CreditSummaryDataCollectionException ex = assertThrows(CreditSummaryDataCollectionException.class,
                () -> retryingExecutor.executeWithRetry(
                        holder,
                        HttpMethod.GET,
                        "exhaustedRetry",
                        "/sample",
                        () -> holder.webClient().get().uri("/sample"),
                        "GET failed"));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.LAYER_DATA_COLLECTION_FAILURE);
        assertThat(ex.getRetryInfo()).isNotNull();
        assertThat(ex.getRetryInfo().attempted()).isEqualTo(3);
        assertThat(ex.getRetryInfo().exhausted()).isTrue();
        assertThat(ex.getUpstream()).isNotNull();
        assertThat(ex.getUpstream().httpStatus()).isEqualTo(503);
    }

    @Test
    void shouldRetryOnTimeout() {
        // Mocking a timeout by creating a WebClient that fails with TimeoutException
        WebClient client = WebClient.builder()
                .baseUrl("https://example.com")
                .exchangeFunction(request -> Mono.error(new TimeoutException("timed out")))
                .build();

        WebClientHolder holder = new WebClientHolder("sample-api", "https://example.com", client);

        CreditSummaryDataCollectionException ex = assertThrows(CreditSummaryDataCollectionException.class,
                () -> retryingExecutor.executeWithRetry(
                        holder,
                        HttpMethod.GET,
                        "timeoutRetry",
                        "/sample",
                        () -> holder.webClient().get().uri("/sample"),
                        "GET failed"));

        assertThat(ex.getRetryInfo().attempted()).isEqualTo(3);
        assertThat(ex.getCause()).isInstanceOf(TimeoutException.class);
    }

    private WebClient clientReturning(ClientResponse response) {
        return WebClient.builder()
                .baseUrl("https://example.com")
                .exchangeFunction(request -> Mono.just(response))
                .build();
    }
}
