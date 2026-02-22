package com.cfbl.platform.core.executor;

import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.exception.core.UpstreamInfo;
import com.cfbl.platform.core.integration.model.ProviderResult;
import com.cfbl.platform.core.retry.RetryPolicyExecutor;
import com.cfbl.platform.core.retry.RetrySettings;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
     * Executes a provider call and returns integration-layer result (no API
     * envelope coupling).
     */
    public Mono<ProviderResult<String>> executeProvider(
            WebClientHolder holder,
            HttpMethod httpMethod,
            String operation,
            String path,
            Supplier<RequestHeadersSpec<?>> requestFactory,
            String failureMessage) {
        return executeProvider(
                holder,
                httpMethod,
                operation,
                path,
                requestFactory,
                failureMessage,
                throwable -> false);
    }

    /**
     * Executes a provider call and returns integration-layer result (no API
     * envelope coupling).
     */
    public Mono<ProviderResult<String>> executeProvider(
            WebClientHolder holder,
            HttpMethod httpMethod,
            String operation,
            String path,
            Supplier<RequestHeadersSpec<?>> requestFactory,
            String failureMessage,
            Predicate<Throwable> callerRetryablePredicate) {

        org.springframework.security.core.context.SecurityContext threadLocalSecurityContext = org.springframework.security.core.context.SecurityContextHolder
                .getContext();

        return Mono.defer(() -> {
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
        })
                .contextWrite(ctx -> {
                    if (threadLocalSecurityContext != null && threadLocalSecurityContext.getAuthentication() != null) {
                        return ctx.put("CURRENT_AUTHENTICATION", threadLocalSecurityContext.getAuthentication());
                    }
                    return ctx;
                });
    }

    private Mono<ProviderResult<String>> executeAttempt(
            Supplier<RequestHeadersSpec<?>> requestFactory,
            DataProviderContext baseContext,
            Instant start) {
        return requestFactory.get()
                .exchangeToMono(response -> mapResponse(
                        response.statusCode(),
                        response.bodyToMono(String.class),
                        baseContext,
                        start))
                .timeout(Duration.ofSeconds(3));
    }

    private Mono<ProviderResult<String>> mapResponse(
            HttpStatusCode statusCode,
            Mono<String> bodyMono,
            DataProviderContext baseContext,
            Instant start) {
        if (statusCode.is2xxSuccessful()) {
            DataProviderContext context = withResponseTime(baseContext, start);
            int responseStatus = statusCode.value();
            return bodyMono
                    .map(body -> ProviderResult.success(responseStatus, body, context))
                    .switchIfEmpty(Mono.fromSupplier(() -> ProviderResult.success(responseStatus, null, context)));
        }

        return bodyMono
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> Mono.just("<unreadable or timeout>"))
                .defaultIfEmpty("")
                .flatMap(errorBody -> {
                    String errorMessage = "Upstream returned HTTP " + statusCode.value();
                    if (!errorBody.isEmpty()) {
                        String truncated = errorBody.length() > 1000 ? errorBody.substring(0, 1000) + "..." : errorBody;
                        errorMessage += " Response: " + truncated;
                    }

                    return Mono.error(new CreditSummaryDataCollectionException(
                            ErrorCode.LAYER_DATA_COLLECTION_FAILURE,
                            errorMessage,
                            withResponseTime(baseContext, start),
                            new UpstreamInfo(statusCode.value(), statusCode.toString(), elapsedMs(start)),
                            null));
                });
    }

    /**
     * Determines whether a REST failure is transient and therefore safe to retry.
     *
     * <p>
     * Retryable conditions:
     * <ul>
     * <li>{@link TimeoutException}: transient timeout while waiting for upstream
     * response</li>
     * <li>{@code CreditSummaryDataCollectionException} with upstream HTTP status:
     * <ul>
     * <li>{@code 429 Too Many Requests}</li>
     * <li>{@code 502 Bad Gateway}</li>
     * <li>{@code 503 Service Unavailable}</li>
     * <li>{@code 504 Gateway Timeout}</li>
     * </ul>
     * </li>
     * </ul>
     *
     * <p>
     * Non-retryable conditions:
     * <ul>
     * <li>other {@code 4xx} statuses (for example {@code 400}, {@code 401},
     * {@code 403}, {@code 404})</li>
     * <li>business/presentation exceptions and unknown non-transient failures</li>
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
