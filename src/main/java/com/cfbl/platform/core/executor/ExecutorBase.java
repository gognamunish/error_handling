package com.cfbl.platform.core.executor;

import com.cfbl.platform.core.exception.api.ApiResponse;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.retry.RetryInfo;
import com.cfbl.platform.core.retry.RetryPolicyExecutor;
import com.cfbl.platform.core.retry.RetrySettings;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/**
 * Shared base for outbound executors with retry, response enrichment, and failure mapping.
 *
 * <p>Protocol executors delegate common concerns here:
 * retry execution, retry metadata attachment, context timing updates, and conversion of unexpected
 * failures through protocol-owned failure mappers.
 */
abstract class ExecutorBase {

    private final RetryPolicyExecutor retryExecutor;

    protected ExecutorBase(RetryPolicyExecutor retryExecutor) {
        this.retryExecutor = Objects.requireNonNull(retryExecutor, "retryExecutor");
    }

    protected <T> Mono<ApiResponse<T>> executeWithRetry(
            String retryName,
            RetrySettings retrySettings,
            Supplier<Mono<ApiResponse<T>>> executeAttempt,
            Predicate<Throwable> retryable,
            FailureMapper failureMapper) {
        AtomicInteger attempts = new AtomicInteger();
        int maxAttempts = retrySettings.effectiveMaxAttempts();

        return retryExecutor.execute(
                        retryName,
                        retrySettings,
                        () -> Mono.defer(() -> {
                            attempts.incrementAndGet();
                            return executeAttempt.get();
                        }),
                        retryable)
                .map(response -> withRetryInfo(response, buildRetryInfo(attempts.get(), maxAttempts, false)))
                .onErrorMap(ex -> {
                    boolean exhausted = retrySettings.enabled()
                            && attempts.get() >= maxAttempts
                            && retryable.test(ex);
                    CreditSummaryPlatformException mapped = failureMapper.map(ex);
                    mapped.attachRetryInfo(buildRetryInfo(attempts.get(), maxAttempts, exhausted));
                    return mapped;
                });
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

    private <T> ApiResponse<T> withRetryInfo(ApiResponse<T> response, RetryInfo retryInfo) {
        return new ApiResponse<>(
                response.timestamp(),
                response.traceId(),
                response.status(),
                response.data(),
                response.metadata(),
                retryInfo,
                response.error());
    }

    @FunctionalInterface
    /**
     * Maps raw failures into a platform exception carrying context.
     */
    protected interface FailureMapper {
        CreditSummaryPlatformException map(Throwable throwable);
    }
}
