package com.cfbl.platform.core.executor;

import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.exception.core.UpstreamInfo;
import com.cfbl.platform.core.integration.model.ProviderResult;
import com.cfbl.platform.core.retry.RetryPolicyExecutor;
import com.cfbl.platform.core.retry.RetrySettings;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import reactor.core.publisher.Mono;

/**
 * Executes outbound REST calls and maps results into {@link ApiResponse}.
 */
@Component
public class RestCallExecutor extends ExecutorBase {

    public RestCallExecutor(RetryPolicyExecutor retryExecutor) {
        super(retryExecutor);
    }

    /**
     * Executes a provider call and returns integration-layer result (no API envelope coupling).
     */
    public <T> Mono<ProviderResult<T>> executeProvider(
            WebClientHolder holder,
            HttpMethod httpMethod,
            String operation,
            String path,
            Supplier<RequestHeadersSpec<?>> requestFactory,
            Type responseType,
            String failureMessage) {
        return executeProvider(
                holder,
                httpMethod,
                operation,
                path,
                requestFactory,
                responseType,
                failureMessage,
                throwable -> false);
    }

    /**
     * Executes a provider call and returns integration-layer result (no API envelope coupling).
     */
    public <T> Mono<ProviderResult<T>> executeProvider(
            WebClientHolder holder,
            HttpMethod httpMethod,
            String operation,
            String path,
            Supplier<RequestHeadersSpec<?>> requestFactory,
            Type responseType,
            String failureMessage,
            Predicate<Throwable> callerRetryablePredicate) {
        return Mono.defer(() -> {
            @SuppressWarnings("unchecked")
            ParameterizedTypeReference<T> bodyType = (ParameterizedTypeReference<T>) ParameterizedTypeReference
                    .forType(responseType);

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
            Predicate<Throwable> effectiveRetryable =
                    throwable -> isRetryableException(throwable) || callerRetryablePredicate.test(throwable);
            return executeWithRetry(
                    "rest:" + holder.serviceId(),
                    retrySettings,
                    () -> executeAttempt(requestFactory, bodyType, baseContext, start),
                    effectiveRetryable,
                    ex -> toPlatformException(ex, failureMessage, baseContext, start));
        });
    }

    private <T> Mono<ProviderResult<T>> executeAttempt(
            Supplier<RequestHeadersSpec<?>> requestFactory,
            ParameterizedTypeReference<T> bodyType,
            DataProviderContext baseContext,
            Instant start) {
        return requestFactory.get()
                .exchangeToMono(response -> mapResponse(
                        response.statusCode(),
                        response.bodyToMono(bodyType),
                        baseContext,
                        start))
                .timeout(Duration.ofSeconds(3));
    }

    private <T> Mono<ProviderResult<T>> mapResponse(
            HttpStatusCode statusCode,
            Mono<T> bodyMono,
            DataProviderContext baseContext,
            Instant start) {
        if (statusCode.is2xxSuccessful()) {
            DataProviderContext context = withResponseTime(baseContext, start);
            int responseStatus = statusCode.value();
            return bodyMono
                    .map(body -> ProviderResult.success(responseStatus, body, context))
                    .switchIfEmpty(Mono.fromSupplier(() -> ProviderResult.success(responseStatus, null, context)));
        }

        return Mono.error(new CreditSummaryDataCollectionException(
                ErrorCode.LAYER_DATA_COLLECTION_FAILURE,
                "Upstream returned HTTP " + statusCode.value(),
                withResponseTime(baseContext, start),
                new UpstreamInfo(statusCode.value(), statusCode.toString(), elapsedMs(start)),
                null));
    }

    /**
     * Determines whether a REST failure is transient and therefore safe to retry.
     *
     * <p>Retryable conditions:
     * <ul>
     *   <li>{@link TimeoutException}: transient timeout while waiting for upstream response</li>
     *   <li>{@code CreditSummaryDataCollectionException} with upstream HTTP status:
     *     <ul>
     *       <li>{@code 429 Too Many Requests}</li>
     *       <li>{@code 502 Bad Gateway}</li>
     *       <li>{@code 503 Service Unavailable}</li>
     *       <li>{@code 504 Gateway Timeout}</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p>Non-retryable conditions:
     * <ul>
     *   <li>other {@code 4xx} statuses (for example {@code 400}, {@code 401}, {@code 403}, {@code 404})</li>
     *   <li>business/presentation exceptions and unknown non-transient failures</li>
     * </ul>
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof TimeoutException) {
            return true;
        }

        if (throwable instanceof CreditSummaryDataCollectionException ex) {
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
