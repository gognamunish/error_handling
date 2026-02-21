package com.cfbl.platform.core.executor;

import static org.assertj.core.api.Assertions.assertThat;

import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.integration.model.ProviderResult;
import com.cfbl.platform.core.retry.RetryPolicyExecutor;
import com.cfbl.platform.core.retry.RetrySettings;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SoapCallExecutorTest {

    private final SoapCallExecutor executor = new SoapCallExecutor(new RetryPolicyExecutor());
    private final SoapCallExecutor retryingExecutor = new SoapCallExecutor(
        new RetryPolicyExecutor(new RetrySettings(true, 3, 1))
    );

    @Test
    void shouldReturnSuccessApiResponse() {
        Mono<ProviderResult<String>> result = executor.executeProvider(
            "soap-sample",
            "https://soap.example.com/service",
            "sayHello",
            () -> "hello-munish",
            "SOAP call failed"
        );

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.status()).isEqualTo(200);
                assertThat(response.data()).isEqualTo("hello-munish");
                assertThat(response.metadata()).isNotNull();
                assertThat(response.metadata().protocol()).isEqualTo(com.cfbl.platform.core.exception.core.DataProviderContext.Protocol.SOAP);
                assertThat(response.metadata().serviceId()).isEqualTo("soap-sample");
                assertThat(response.metadata().protocolAttributes().get("operation")).isEqualTo("sayHello");
                assertThat(response.retry()).isNotNull();
                assertThat(response.retry().attempted()).isEqualTo(1);
            })
            .verifyComplete();
    }

    @Test
    void shouldRetryAndSucceedOnThirdAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        Mono<ProviderResult<String>> result = retryingExecutor.executeProvider(
            "soap-sample",
            "https://soap.example.com/service",
            "sayHello",
            () -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new RuntimeException(new TimeoutException("soap timeout"));
                }
                return "hello-after-retry";
            },
            "SOAP call failed",
            new RetrySettings(true, 3, 1),
            ex -> false
        );

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.data()).isEqualTo("hello-after-retry");
                assertThat(response.retry()).isNotNull();
                assertThat(response.retry().attempted()).isEqualTo(3);
                assertThat(response.retry().retried()).isTrue();
                assertThat(response.retry().exhausted()).isFalse();
            })
            .verifyComplete();
    }

    @Test
    void shouldMapExhaustedRetryToDataCollectionException() {
        Mono<ProviderResult<String>> result = retryingExecutor.executeProvider(
            "soap-sample",
            "https://soap.example.com/service",
            "sayHello",
            () -> {
                throw new RuntimeException(new TimeoutException("soap timeout"));
            },
            "SOAP call failed",
            new RetrySettings(true, 3, 1),
            ex -> false
        );

        StepVerifier.create(result)
            .expectErrorSatisfies(ex -> {
                assertThat(ex).isInstanceOf(CreditSummaryDataCollectionException.class);
                CreditSummaryDataCollectionException cse = (CreditSummaryDataCollectionException) ex;
                assertThat(cse.getMessage()).isEqualTo("SOAP call failed");
                assertThat(cse.getRetryInfo()).isNotNull();
                assertThat(cse.getRetryInfo().attempted()).isEqualTo(3);
                assertThat(cse.getRetryInfo().exhausted()).isTrue();
                assertThat(cse.getProviderContext()).isNotNull();
                assertThat(cse.getProviderContext().protocol()).isEqualTo(com.cfbl.platform.core.exception.core.DataProviderContext.Protocol.SOAP);
            })
            .verify();
    }

    @Test
    void shouldRetryUsingCallerProvidedPredicate() {
        AtomicInteger attempts = new AtomicInteger();
        Mono<ProviderResult<String>> result = retryingExecutor.executeProvider(
            "soap-sample",
            "https://soap.example.com/service",
            "sayHello",
            () -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("custom transient");
            },
            "SOAP call failed",
            new RetrySettings(true, 3, 1),
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
}
