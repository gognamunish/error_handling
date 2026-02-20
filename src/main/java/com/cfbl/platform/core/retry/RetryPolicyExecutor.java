package com.cfbl.platform.core.retry;

import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Applies named Resilience4j retry policies to reactive provider calls.
 *
 * <p>Usage: wrap an existing {@code Supplier<Mono<T>>} without changing WebClient request construction.
 * Retry key is service-level: use {@code serviceId} as the retry name to keep retry instances stable
 * and low-cardinality across operations.
 */
@Slf4j
@Component
public class RetryPolicyExecutor {

    private final RetrySettings defaultSettings;
    private final RetryRegistry retryRegistry;
    private final Set<String> retryLoggersRegistered = ConcurrentHashMap.newKeySet();

    public RetryPolicyExecutor() {
        this(RetrySettings.defaults());
    }

    public RetryPolicyExecutor(RetrySettings settings) {
        this.defaultSettings = Objects.requireNonNull(settings, "settings");
        this.retryRegistry = RetryRegistry.ofDefaults();
    }

    /**
     * Executes the supplied reactive call with retry behavior when enabled.
     *
     * @param retryName service-level policy key (recommended: {@code serviceId}) used for retry instance reuse and logging
     * @param supplier call supplier returning a fresh publisher per subscription
     * @param retryPredicate predicate that marks which exceptions are retryable
     * @param <T> payload type
     * @return publisher decorated with configured retry policy
     */
    public <T> Mono<T> execute(
            String retryName,
            Supplier<Mono<T>> supplier,
            Predicate<Throwable> retryPredicate) {
        return execute(retryName, defaultSettings, supplier, retryPredicate);
    }

    /**
     * Executes the supplied reactive call with service-specific retry settings.
     */
    public <T> Mono<T> execute(
            String retryName,
            RetrySettings settings,
            Supplier<Mono<T>> supplier,
            Predicate<Throwable> retryPredicate) {
        Objects.requireNonNull(retryName, "retryName");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(retryPredicate, "retryPredicate");

        if (!settings.enabled()) {
            return supplier.get();
        }

        String retryKey = toRetryKey(retryName, settings);
        Retry retry = retryRegistry.retry(retryKey, buildRetryConfig(settings, retryPredicate));
        registerRetryLoggingOnce(retryKey, retryName, settings, retry);
        return supplier.get().transformDeferred(RetryOperator.of(retry));
    }

    private RetryConfig buildRetryConfig(RetrySettings settings, Predicate<Throwable> retryPredicate) {
        return RetryConfig.custom()
            .maxAttempts(settings.maxAttempts())
            .waitDuration(Duration.ofMillis(settings.waitDurationMs()))
            .retryOnException(retryPredicate::test)
            .failAfterMaxAttempts(true)
            .build();
    }

    private String toRetryKey(String retryName, RetrySettings settings) {
        return retryName + "|" + settings.maxAttempts() + "|" + settings.waitDurationMs() + "|" + settings.enabled();
    }

    private void registerRetryLoggingOnce(
        String retryKey,
        String retryName,
        RetrySettings settings,
        Retry retry
    ) {
        if (!retryLoggersRegistered.add(retryKey)) {
            return;
        }

        retry.getEventPublisher().onRetry(event -> log.warn(
                "Retrying provider call name={}, attempt={}, maxAttempts={}, cause={}",
                retryName,
                event.getNumberOfRetryAttempts(),
                settings.maxAttempts(),
                event.getLastThrowable() == null ? "N/A" : event.getLastThrowable().getMessage()));
    }
}
