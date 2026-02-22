package com.cfbl.platform.core.executor.nonreactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.integration.model.ProviderResult;
import com.cfbl.platform.core.retry.RetrySettings;
import com.cfbl.platform.core.retry.SyncRetryPolicyExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SyncSoapCallExecutorTest {

    private final SyncSoapCallExecutor executor = new SyncSoapCallExecutor(new SyncRetryPolicyExecutor());
    private final SyncSoapCallExecutor retryingExecutor = new SyncSoapCallExecutor(
            new SyncRetryPolicyExecutor(new RetrySettings(true, 3, 1)));

    @Test
    void shouldReturnSuccessApiResponse() {
        ProviderResult<String> result = executor.executeWithRetry(
                "soap-sample",
                "https://soap.example.com/service",
                "sayHello",
                () -> "hello-sync",
                "SOAP call failed");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.data()).isEqualTo("hello-sync");
        assertThat(result.metadata()).isNotNull();
        assertThat(result.metadata().protocol())
                .isEqualTo(com.cfbl.platform.core.exception.core.DataProviderContext.Protocol.SOAP);
        assertThat(result.metadata().responseTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.retry()).isNotNull();
        assertThat(result.retry().attempted()).isEqualTo(1);
    }

    @Test
    void shouldRetryAndSucceedOnThirdAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        ProviderResult<String> result = retryingExecutor.executeWithRetry(
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
                ex -> false);

        assertThat(result.data()).isEqualTo("hello-after-retry");
        assertThat(result.retry().attempted()).isEqualTo(3);
        assertThat(result.retry().retried()).isTrue();
    }

    @Test
    void shouldMapExhaustedRetryToDataCollectionException() {
        CreditSummaryDataCollectionException ex = assertThrows(CreditSummaryDataCollectionException.class,
                () -> retryingExecutor.executeWithRetry(
                        "soap-sample",
                        "https://soap.example.com/service",
                        "sayHello",
                        () -> {
                            throw new RuntimeException(new TimeoutException("soap timeout"));
                        },
                        "SOAP call failed",
                        new RetrySettings(true, 3, 1),
                        throwable -> false));

        assertThat(ex.getMessage()).isEqualTo("SOAP call failed");
        assertThat(ex.getRetryInfo().attempted()).isEqualTo(3);
        assertThat(ex.getRetryInfo().exhausted()).isTrue();
        assertThat(ex.getProviderContext().responseTimeMs()).isGreaterThanOrEqualTo(0);
    }
}
