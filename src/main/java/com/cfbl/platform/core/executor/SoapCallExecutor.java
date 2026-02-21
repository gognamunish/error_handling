package com.cfbl.platform.core.executor;

import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.exception.core.UpstreamInfo;
import com.cfbl.platform.core.integration.model.ProviderResult;
import com.cfbl.platform.core.retry.RetryPolicyExecutor;
import com.cfbl.platform.core.retry.RetrySettings;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Executes outbound SOAP port calls and maps results into integration-layer {@code ProviderResult}.
 *
 * <p>SOAP invocation is blocking. The supplier call executes on the caller/subscriber thread
 * (no internal scheduler offload), so thread-local request/security context is preserved.
 */
@Component
public class SoapCallExecutor extends ExecutorBase {

    private static final RetrySettings DEFAULT_RETRY_SETTINGS = RetrySettings.defaults();

    public SoapCallExecutor(RetryPolicyExecutor retryExecutor) {
        super(retryExecutor);
    }

    /**
     * Executes a SOAP provider call and returns integration-layer result (no API envelope coupling).
     */
    public <T> Mono<ProviderResult<T>> executeProvider(
            String serviceId,
            String endpointUrl,
            String operation,
            Supplier<T> portCallSupplier,
            String failureMessage,
            RetrySettings retrySettings,
            Predicate<Throwable> callerRetryablePredicate) {
        return Mono.defer(() -> {
            Objects.requireNonNull(serviceId, "serviceId");
            Objects.requireNonNull(endpointUrl, "endpointUrl");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(portCallSupplier, "portCallSupplier");
            Objects.requireNonNull(failureMessage, "failureMessage");
            Objects.requireNonNull(retrySettings, "retrySettings");
            Objects.requireNonNull(callerRetryablePredicate, "callerRetryablePredicate");

            Instant collectedAt = Instant.now();
            Instant start = collectedAt;

            DataProviderContext baseContext = new DataProviderContext(
                    DataProviderContext.Protocol.SOAP,
                    serviceId,
                    endpointUrl,
                    Map.of("operation", operation),
                    0L,
                    collectedAt);

            Predicate<Throwable> effectiveRetryable =
                    throwable -> isRetryableException(throwable) || callerRetryablePredicate.test(throwable);
            return executeWithRetry(
                    "soap:" + serviceId,
                    retrySettings,
                    () -> executeAttempt(portCallSupplier, baseContext, start),
                    effectiveRetryable,
                    ex -> toPlatformException(ex, failureMessage, baseContext, start));
        });
    }

    /**
     * Executes a SOAP provider call using default retry settings.
     */
    public <T> Mono<ProviderResult<T>> executeProvider(
            String serviceId,
            String endpointUrl,
            String operation,
            Supplier<T> portCallSupplier,
            String failureMessage) {
        return executeProvider(
                serviceId,
                endpointUrl,
                operation,
                portCallSupplier,
                failureMessage,
                DEFAULT_RETRY_SETTINGS,
                throwable -> false);
    }

    private <T> Mono<ProviderResult<T>> executeAttempt(
            Supplier<T> portCallSupplier,
            DataProviderContext baseContext,
            Instant start) {
        DataProviderContext responseContext = withResponseTime(baseContext, start);
        return Mono.fromCallable(portCallSupplier::get)
                .map(body -> ProviderResult.success(HttpStatus.OK.value(), body, responseContext))
                .switchIfEmpty(Mono.fromSupplier(() -> ProviderResult.success(HttpStatus.OK.value(), null, responseContext)))
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
