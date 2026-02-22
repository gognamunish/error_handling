package com.cfbl.platform.core.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class SyncRetryPolicyExecutorTest {

    private final SyncRetryPolicyExecutor executor = new SyncRetryPolicyExecutor(new RetrySettings(true, 3, 1));

    @Test
    void shouldSucceedOnFirstAttempt() {
        String result = executor.executeSync(
                "test-retry",
                () -> "success",
                ex -> true);

        assertThat(result).isEqualTo("success");
    }

    @Test
    void shouldRetryAndSucceed() {
        AtomicInteger attempts = new AtomicInteger();
        String result = executor.executeSync(
                "test-retry-fail",
                () -> {
                    if (attempts.incrementAndGet() < 2) {
                        throw new RuntimeException("transient");
                    }
                    return "success-after-retry";
                },
                ex -> true);

        assertThat(result).isEqualTo("success-after-retry");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldFailAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(RuntimeException.class, () -> executor.executeSync(
                "test-retry-exhaust",
                () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("permanent");
                },
                ex -> true));

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryOnNonRetryableException() {
        AtomicInteger attempts = new AtomicInteger();
        Predicate<Throwable> isRetryable = ex -> ex.getMessage().equals("retryable");

        assertThrows(RuntimeException.class, () -> executor.executeSync(
                "test-retry-selective",
                () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("non-retryable");
                },
                isRetryable));

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void shouldRespectDisabledRetrySettings() {
        SyncRetryPolicyExecutor disabledExecutor = new SyncRetryPolicyExecutor(new RetrySettings(false, 3, 1));
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(RuntimeException.class, () -> disabledExecutor.executeSync(
                "test-disabled",
                () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("fail");
                },
                ex -> true));

        assertThat(attempts.get()).isEqualTo(1);
    }
}
