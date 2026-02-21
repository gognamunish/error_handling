package com.cfbl.platform.core.executor;

import com.cfbl.platform.core.exception.api.ApiResponse;
import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.exception.core.UpstreamInfo;
import com.cfbl.platform.core.retry.RetryPolicyExecutor;
import com.cfbl.platform.core.retry.RetrySettings;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Executes outbound SOAP port calls and maps results into {@link ApiResponse}.
 */
@Component
public class SoapCallExecutor extends ExecutorBase {

    public SoapCallExecutor(RetryPolicyExecutor retryExecutor) {
        super(retryExecutor);
    }

    /**
     * Executes a SOAP provider call with retry support and unified response mapping.
     */
    public <T> Mono<ApiResponse<T>> execute(
            SoapClientHolder holder,
            String operation,
            Supplier<T> portCallSupplier,
            String failureMessage) {
        return execute(holder, operation, portCallSupplier, failureMessage, throwable -> false);
    }

    /**
     * Executes a SOAP provider call and allows caller-specific transient retry rules to be appended.
     */
    public <T> Mono<ApiResponse<T>> execute(
            SoapClientHolder holder,
            String operation,
            Supplier<T> portCallSupplier,
            String failureMessage,
            Predicate<Throwable> callerRetryablePredicate) {
        return Mono.defer(() -> {
            Instant collectedAt = Instant.now();
            Instant start = collectedAt;

            DataProviderContext baseContext = new DataProviderContext(
                    DataProviderContext.Protocol.SOAP,
                    holder.serviceId(),
                    holder.endpointUrl(),
                    Map.of("operation", operation),
                    0L,
                    collectedAt);

            RetrySettings retrySettings = holder.retrySettings();
            Predicate<Throwable> effectiveRetryable =
                    throwable -> isRetryableException(throwable) || callerRetryablePredicate.test(throwable);
            return executeWithRetry(
                    "soap:" + holder.serviceId(),
                    retrySettings,
                    () -> executeAttempt(portCallSupplier, baseContext, start),
                    effectiveRetryable,
                    ex -> toPlatformException(ex, failureMessage, baseContext, start));
        });
    }

    private <T> Mono<ApiResponse<T>> executeAttempt(
            Supplier<T> portCallSupplier,
            DataProviderContext baseContext,
            Instant start) {
        DataProviderContext responseContext = withResponseTime(baseContext, start);
        return Mono.fromCallable(portCallSupplier::get)
                .subscribeOn(Schedulers.boundedElastic())
                .map(body -> ApiResponse.success(body, HttpStatus.OK.value(), responseContext))
                .switchIfEmpty(Mono.fromSupplier(
                        () -> ApiResponse.success(null, HttpStatus.OK.value(), responseContext)))
                .timeout(Duration.ofSeconds(3));
    }

    /**
     * Determines whether a SOAP failure is transient and safe to retry.
     *
     * <p>Retryable conditions:
     * <ul>
     *   <li>{@link TimeoutException}, {@link SocketTimeoutException}, {@link ConnectException}</li>
     *   <li>cause-chain contains one of the above timeout/connect exceptions</li>
     *   <li>{@link CreditSummaryDataCollectionException} produced by upstream transport failures</li>
     * </ul>
     *
     * <p>Non-retryable conditions:
     * <ul>
     *   <li>business/presentation exceptions</li>
     *   <li>explicit non-transient SOAP contract/validation faults not mapped as transport failures</li>
     * </ul>
     */
    private boolean isRetryableException(Throwable throwable) {
        return throwable instanceof TimeoutException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof ConnectException
                || hasCause(throwable, TimeoutException.class)
                || hasCause(throwable, SocketTimeoutException.class)
                || hasCause(throwable, ConnectException.class)
                || throwable instanceof CreditSummaryDataCollectionException;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private CreditSummaryPlatformException toPlatformException(
            Throwable throwable,
            String failureMessage,
            DataProviderContext baseContext,
            Instant start) {
        if (throwable instanceof CreditSummaryPlatformException platformException) {
            return platformException;
        }

        return new CreditSummaryDataCollectionException(
                ErrorCode.LAYER_DATA_COLLECTION_FAILURE,
                failureMessage,
                withResponseTime(baseContext, start),
                new UpstreamInfo(null, throwable.getMessage(), elapsedMs(start)),
                throwable);
    }
}
