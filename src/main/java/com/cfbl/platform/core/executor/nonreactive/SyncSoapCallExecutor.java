package com.cfbl.platform.core.executor.nonreactive;

import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.exception.core.UpstreamInfo;
import com.cfbl.platform.core.integration.model.ProviderResult;
import com.cfbl.platform.core.retry.RetrySettings;
import com.cfbl.platform.core.retry.SyncRetryPolicyExecutor;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Executes outbound blocking SOAP port calls and maps results into
 * integration-layer {@code ProviderResult}.
 * <p>
 * This executor prevents context loss by executing Resilience4j retry blocks
 * strictly
 * on the calling thread, maintaining automatic access to ThreadLocal
 * SecurityContext variables.
 */
@Component
public class SyncSoapCallExecutor extends SyncExecutorBase {

    private static final RetrySettings DEFAULT_RETRY_SETTINGS = RetrySettings.defaults();

    public SyncSoapCallExecutor(SyncRetryPolicyExecutor retryExecutor) {
        super(retryExecutor);
    }

    /**
     * Executes a synchronous SOAP provider call with full retry and failure
     * handling.
     *
     * @param serviceId                Logical service identifier for the upstream
     *                                 provider
     * @param endpointUrl              Physical URL of the SOAP service
     * @param operation                Name of the SOAP operation being called
     * @param portCallSupplier         Lambda or method reference executing the
     *                                 actual JAX-WS port call
     * @param failureMessage           Error message prefix if the call fails after
     *                                 retries
     * @param retrySettings            Configuration for retry behavior
     * @param callerRetryablePredicate Additional logic to determine if an error is
     *                                 transient
     * @param <T>                      Type of the expected response object
     * @return result containing the successful response payload and context
     * @throws CreditSummaryPlatformException if the call fails after exhausting
     *                                        retries
     */
    public <T> ProviderResult<T> executeProvider(
            String serviceId,
            String endpointUrl,
            String operation,
            Supplier<T> portCallSupplier,
            String failureMessage,
            RetrySettings retrySettings,
            Predicate<Throwable> callerRetryablePredicate) {

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

        Predicate<Throwable> effectiveRetryable = throwable -> isRetryableException(throwable)
                || callerRetryablePredicate.test(throwable);

        return executeWithRetry(
                "soap:" + serviceId,
                retrySettings,
                () -> executeAttempt(portCallSupplier, baseContext, start),
                effectiveRetryable,
                ex -> toPlatformException(ex, failureMessage, baseContext, start));
    }

    /**
     * Executes a synchronous SOAP provider call using default retry settings.
     *
     * @param serviceId        Logical service identifier
     * @param endpointUrl      Physical URL
     * @param operation        SOAP operation name
     * @param portCallSupplier Lambda executing the call
     * @param failureMessage   Failure message prefix
     * @param <T>              Response type
     * @return result payload and context
     */
    public <T> ProviderResult<T> executeProvider(
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

    private <T> ProviderResult<T> executeAttempt(
            Supplier<T> portCallSupplier,
            DataProviderContext baseContext,
            Instant start) {
        T body = portCallSupplier.get(); // Call first
        DataProviderContext responseContext = withResponseTime(baseContext, start); // Then calculate time
        return ProviderResult.success(HttpStatus.OK.value(), body, responseContext);
    }

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
