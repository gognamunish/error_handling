package com.cfbl.platform.core.executor.nonreactive;

import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.integration.model.ProviderResult;
import com.cfbl.platform.core.retry.RetryInfo;
import com.cfbl.platform.core.retry.RetrySettings;
import com.cfbl.platform.core.retry.SyncRetryPolicyExecutor;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shared base for outbound synchronous executors with retry, response
 * enrichment, and failure mapping.
 */
abstract class SyncExecutorBase {

    private final SyncRetryPolicyExecutor retryExecutor;

    protected SyncExecutorBase(SyncRetryPolicyExecutor retryExecutor) {
        this.retryExecutor = Objects.requireNonNull(retryExecutor, "retryExecutor");
    }

    /**
     * Internal template method that orchestrates the retry loop for synchronous
     * execution.
     * Takes care of incrementing attempt counts and attaching final retry metadata
     * to either the success result or the failure exception.
     *
     * @param retryName      Unique name for the retry instance (e.g.
     *                       "rest:service-id")
     * @param retrySettings  Configuration for max attempts and wait duration
     * @param executeAttempt Supplier that executes a single isolated attempt
     * @param retryable      Predicate to determine if an exception triggers a retry
     * @param failureMapper  Function to map raw technical exceptions into platform
     *                       exceptions
     * @param <T>            Response payload type
     * @return enriched provider result including retry metadata
     * @throws CreditSummaryPlatformException if call fails or exhausts retries
     */
    protected <T> ProviderResult<T> executeWithRetry(
            String retryName,
            RetrySettings retrySettings,
            Supplier<ProviderResult<T>> executeAttempt,
            Predicate<Throwable> retryable,
            FailureMapper failureMapper) {
        AtomicInteger attempts = new AtomicInteger();
        int maxAttempts = retrySettings.effectiveMaxAttempts();

        try {
            ProviderResult<T> response = retryExecutor.executeSync(
                    retryName,
                    retrySettings,
                    () -> {
                        attempts.incrementAndGet();
                        return executeAttempt.get();
                    },
                    retryable);
            return withRetryInfo(response, buildRetryInfo(attempts.get(), maxAttempts, false));
        } catch (Throwable ex) {
            boolean exhausted = retrySettings.enabled()
                    && attempts.get() >= maxAttempts
                    && retryable.test(ex);
            CreditSummaryPlatformException mapped = failureMapper.map(ex);
            mapped.attachRetryInfo(buildRetryInfo(attempts.get(), maxAttempts, exhausted));
            throw mapped;
        }
    }

    protected DataProviderContext withResponseTime(DataProviderContext base, Instant start) {
        return new DataProviderContext(
                base.protocol(),
                base.serviceId(),
                base.endpoint(),
                base.protocolAttributes(),
                elapsedMs(start),
                base.collectedAt());
    }

    protected long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }

    private RetryInfo buildRetryInfo(int attempted, int maxAttempts, boolean exhausted) {
        return new RetryInfo(
                Math.max(1, attempted),
                maxAttempts,
                attempted > 1,
                exhausted);
    }

    private <T> ProviderResult<T> withRetryInfo(ProviderResult<T> response, RetryInfo retryInfo) {
        return new ProviderResult<>(
                response.status(),
                response.data(),
                response.metadata(),
                retryInfo);
    }

    @FunctionalInterface
    /**
     * Maps raw failures into a platform exception carrying context.
     */
    protected interface FailureMapper {
        CreditSummaryPlatformException map(Throwable throwable);
    }
}
