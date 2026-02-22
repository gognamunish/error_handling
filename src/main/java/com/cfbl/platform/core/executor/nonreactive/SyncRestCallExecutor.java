package com.cfbl.platform.core.executor.nonreactive;

import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.exception.core.UpstreamInfo;
import com.cfbl.platform.core.executor.WebClientHolder;
import com.cfbl.platform.core.integration.model.ProviderResult;
import com.cfbl.platform.core.retry.RetrySettings;
import com.cfbl.platform.core.retry.SyncRetryPolicyExecutor;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Executes outbound synchronous REST calls using WebClient.block() and maps
 * results into {@link ProviderResult}.
 * <p>
 * This executor prevents context loss by executing Resilience4j retry blocks
 * strictly
 * on the calling thread. By calling .block(), we ensure the request happens
 * synchronously
 * and re-evaluates all WebClient filters/headers on every retry.
 */
@Component
public class SyncRestCallExecutor extends SyncExecutorBase {

    public SyncRestCallExecutor(SyncRetryPolicyExecutor retryExecutor) {
        super(retryExecutor);
    }

    /**
     * Executes a synchronous provider call using the provided request factory.
     * Default retry behavior is applied from the holder settings.
     *
     * @param holder         Contains service metadata and WebClient instance
     * @param httpMethod     HTTP method for metrics/logging
     * @param operation      Logical operation name for metrics/logging
     * @param path           Specific URL path for metrics/logging
     * @param requestFactory Supplier that builds the WebClient request
     *                       specification
     * @param failureMessage Human-readable message to include if the call fails
     * @return result containing the successful response body and context
     * @throws CreditSummaryPlatformException if the call fails after retries
     *                                        (captures timing and retry info)
     */
    public ProviderResult<String> executeWithRetry(
            WebClientHolder holder,
            HttpMethod httpMethod,
            String operation,
            String path,
            Supplier<RequestHeadersSpec<?>> requestFactory,
            String failureMessage) {
        return executeWithRetry(
                holder,
                httpMethod,
                operation,
                path,
                requestFactory,
                failureMessage,
                throwable -> false);
    }

    /**
     * Executes a synchronous provider call with an additional caller-defined retry
     * predicate.
     *
     * @param holder                   Contains service metadata and WebClient
     *                                 instance
     * @param httpMethod               HTTP method for metrics/logging
     * @param operation                Logical operation name for metrics/logging
     * @param path                     Specific URL path for metrics/logging
     * @param requestFactory           Supplier that builds the WebClient request
     *                                 specification
     * @param failureMessage           Human-readable message to include if the call
     *                                 fails
     * @param callerRetryablePredicate Custom logic to determine if an error should
     *                                 trigger a retry
     * @return result containing the successful response body and context
     * @throws CreditSummaryPlatformException if the call fails after retries
     */
    public ProviderResult<String> executeWithRetry(
            WebClientHolder holder,
            HttpMethod httpMethod,
            String operation,
            String path,
            Supplier<RequestHeadersSpec<?>> requestFactory,
            String failureMessage,
            Predicate<Throwable> callerRetryablePredicate) {

        Instant collectedAt = Instant.now();
        Instant start = collectedAt;

        DataProviderContext baseContext = new DataProviderContext(
                DataProviderContext.Protocol.REST,
                holder.serviceId(),
                holder.endpointUrl() + path,
                buildProtocolMeta(httpMethod, operation),
                0L,
                collectedAt);

        RetrySettings retrySettings = holder.retrySettings();
        Predicate<Throwable> effectiveRetryable = throwable -> isRetryableException(throwable)
                || callerRetryablePredicate.test(throwable);

        return executeWithRetry(
                "rest:" + holder.serviceId(),
                retrySettings,
                () -> executeAttempt(requestFactory, baseContext, start),
                effectiveRetryable,
                ex -> toPlatformException(ex, failureMessage, baseContext, start));
    }

    private ProviderResult<String> executeAttempt(
            Supplier<RequestHeadersSpec<?>> requestFactory,
            DataProviderContext baseContext,
            Instant start) {
        try {
            // Re-evaluating requestFactory.get() inside the loop ensures filters re-run
            ResponseEntity<String> response = requestFactory.get()
                    .retrieve()
                    .toEntity(String.class)
                    .block(); // Blocks the caller thread

            if (response == null) {
                throw new CreditSummaryDataCollectionException(
                        ErrorCode.LAYER_DATA_COLLECTION_FAILURE,
                        "Upstream returned empty response",
                        withResponseTime(baseContext, start),
                        new UpstreamInfo(null, "NULL_RESPONSE", elapsedMs(start)),
                        null);
            }

            return mapResponse(response.getStatusCode(), response.getBody(), baseContext, start);
        } catch (WebClientResponseException ex) {
            String errorBody = ex.getResponseBodyAsString();
            String truncated = errorBody.length() > 1000 ? errorBody.substring(0, 1000) + "..." : errorBody;
            String errorMessage = "Upstream returned HTTP " + ex.getStatusCode().value() +
                    (truncated.isEmpty() ? "" : " Response: " + truncated);

            throw new CreditSummaryDataCollectionException(
                    ErrorCode.LAYER_DATA_COLLECTION_FAILURE,
                    errorMessage,
                    withResponseTime(baseContext, start),
                    new UpstreamInfo(ex.getStatusCode().value(), ex.getStatusCode().toString(), elapsedMs(start)),
                    null);
        } catch (Exception ex) {
            // Unwrap ReactiveException from WebClient.block()
            Throwable actual = (ex.getClass().getName().contains("ReactiveException") && ex.getCause() != null)
                    ? ex.getCause()
                    : ex;

            throw new CreditSummaryDataCollectionException(
                    ErrorCode.LAYER_DATA_COLLECTION_FAILURE,
                    "Provider transport or timeout error: " + actual.getMessage(),
                    withResponseTime(baseContext, start),
                    new UpstreamInfo(null, actual.getClass().getSimpleName(), elapsedMs(start)),
                    actual);
        }
    }

    private ProviderResult<String> mapResponse(
            HttpStatusCode statusCode,
            String body,
            DataProviderContext baseContext,
            Instant start) {
        if (statusCode.is2xxSuccessful()) {
            DataProviderContext context = withResponseTime(baseContext, start);
            return ProviderResult.success(statusCode.value(), body, context);
        }

        throw new CreditSummaryDataCollectionException(
                ErrorCode.LAYER_DATA_COLLECTION_FAILURE,
                "Upstream returned HTTP " + statusCode.value() + " Response: " + (body != null ? body : ""),
                withResponseTime(baseContext, start),
                new UpstreamInfo(statusCode.value(), statusCode.toString(), elapsedMs(start)),
                null);
    }

    private boolean isRetryableException(Throwable throwable) {
        // Handle common timeout/connection issues
        if (throwable instanceof TimeoutException || throwable.getCause() instanceof TimeoutException) {
            return true;
        }

        // WebFlux .block() wraps exceptions in ReactiveException
        if (throwable.getClass().getName().contains("ReactiveException") && throwable.getCause() != null) {
            return isRetryableException(throwable.getCause());
        }

        if (throwable instanceof WebClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            return status.equals(HttpStatus.TOO_MANY_REQUESTS)
                    || status.equals(HttpStatus.BAD_GATEWAY)
                    || status.equals(HttpStatus.SERVICE_UNAVAILABLE)
                    || status.equals(HttpStatus.GATEWAY_TIMEOUT);
        }

        if (throwable instanceof CreditSummaryDataCollectionException ex) {
            // Safe cause check
            Throwable cause = ex.getCause();
            if (cause instanceof TimeoutException || (cause != null && cause.getCause() instanceof TimeoutException)) {
                return true;
            }

            UpstreamInfo upstream = ex.getUpstream();
            if (upstream == null || upstream.httpStatus() == null) {
                return false;
            }

            HttpStatusCode status = HttpStatusCode.valueOf(upstream.httpStatus());
            return status.equals(HttpStatus.TOO_MANY_REQUESTS)
                    || status.equals(HttpStatus.BAD_GATEWAY)
                    || status.equals(HttpStatus.SERVICE_UNAVAILABLE)
                    || status.equals(HttpStatus.GATEWAY_TIMEOUT);
        }

        return false;
    }

    private Map<String, String> buildProtocolMeta(HttpMethod httpMethod, String operation) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("httpMethod", httpMethod.name());
        meta.put("operation", operation);
        return Map.copyOf(meta);
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
