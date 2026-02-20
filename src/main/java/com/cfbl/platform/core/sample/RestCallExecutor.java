package com.cfbl.platform.core.sample;

import com.cfbl.platform.core.exception.api.ApiResponse;
import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.exception.core.UpstreamInfo;
import com.cfbl.platform.core.retry.RetryPolicyExecutor;
import com.cfbl.platform.core.retry.RetryInfo;
import com.cfbl.platform.core.retry.RetrySettings;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Executes outbound REST calls and maps results into {@link ApiResponse}.
 */
@Component
public class RestCallExecutor {

    private final RetryPolicyExecutor retryExecutor;

    public RestCallExecutor(RetryPolicyExecutor retryExecutor) {
        this.retryExecutor = Objects.requireNonNull(retryExecutor, "retryExecutor");
    }

    /**
     * Executes a provider call using a supplied WebClient and maps success/error output.
     */
    public <T> Mono<ApiResponse<T>> execute(
            WebClientHolder holder,
            HttpMethod httpMethod,
            String operation,
            String path,
            Function<WebClient, WebClient.RequestHeadersSpec<?>> requestFactory,
            Type responseType,
            String failureMessage) {
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

            AtomicInteger attempts = new AtomicInteger();
            RetrySettings retrySettings = holder.retrySettings();
            int maxAttempts = retrySettings.effectiveMaxAttempts();
            // Service-level retry key keeps RetryRegistry cardinality bounded.
            String retryName = holder.serviceId();

            return retryExecutor.execute(
                    retryName,
                    retrySettings,
                    () -> executeAttempt(requestFactory, holder, bodyType, baseContext, start, attempts),
                    this::isRetryableException)
                .map(response -> addSuccessRetryInfo(response, attempts.get(), maxAttempts))
                .onErrorMap(ex -> mapFailure(
                        ex,
                        failureMessage,
                        baseContext,
                        start,
                        attempts.get(),
                        maxAttempts,
                        retrySettings));
        });
    }

    private <T> Mono<ApiResponse<T>> executeAttempt(
            Function<WebClient, WebClient.RequestHeadersSpec<?>> requestFactory,
            WebClientHolder holder,
            ParameterizedTypeReference<T> bodyType,
            DataProviderContext baseContext,
            Instant start,
            AtomicInteger attempts) {
        return Mono.defer(() -> {
            attempts.incrementAndGet();
            return requestFactory.apply(holder.webClient())
                    .exchangeToMono(response -> mapResponse(
                            response.statusCode(),
                            response.bodyToMono(bodyType),
                            baseContext,
                            start))
                    .timeout(Duration.ofSeconds(3));
        });
    }

    private <T> ApiResponse<T> addSuccessRetryInfo(ApiResponse<T> response, int attempts, int maxAttempts) {
        return withRetryInfo(response, buildRetryInfo(attempts, maxAttempts, false));
    }

    private CreditSummaryPlatformException mapFailure(
            Throwable throwable,
            String failureMessage,
            DataProviderContext baseContext,
            Instant start,
            int attempts,
            int maxAttempts,
            RetrySettings retrySettings) {
        boolean exhausted = retrySettings.enabled()
                && attempts >= maxAttempts
                && isRetryableException(throwable);
        return attachRetryInfo(
                toPlatformException(throwable, failureMessage, baseContext, start),
                buildRetryInfo(attempts, maxAttempts, exhausted));
    }

    private <T> Mono<ApiResponse<T>> mapResponse(
            HttpStatusCode statusCode,
            Mono<T> bodyMono,
            DataProviderContext baseContext,
            Instant start) {
        if (statusCode.is2xxSuccessful()) {
            DataProviderContext context = withResponseTime(baseContext, start);
            int responseStatus = statusCode.value();
            return bodyMono
                    .map(body -> ApiResponse.success(body, responseStatus, context))
                    .switchIfEmpty(Mono.fromSupplier(() -> ApiResponse.success(null, responseStatus, context)));
        }

        return Mono.error(toUpstreamException(statusCode, baseContext, start));
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
                ErrorCode.DATA_COLLECTION_LAYER_EXCEPTION,
                failureMessage,
                withResponseTime(baseContext, start),
                new UpstreamInfo(null, throwable.getMessage(), elapsedMs(start)),
                throwable);
    }

    private CreditSummaryDataCollectionException toUpstreamException(
            HttpStatusCode statusCode,
            DataProviderContext baseContext,
            Instant start) {
        int rawStatus = statusCode.value();

        return new CreditSummaryDataCollectionException(
                ErrorCode.DATA_COLLECTION_LAYER_EXCEPTION,
                "Upstream returned HTTP " + rawStatus,
                    withResponseTime(baseContext, start),
                    new UpstreamInfo(rawStatus, statusCode.toString(), elapsedMs(start)),
                    null);
    }

    private boolean isRetryableException(Throwable throwable) {
        return throwable instanceof TimeoutException
                || throwable instanceof CreditSummaryDataCollectionException;
    }

    private CreditSummaryPlatformException attachRetryInfo(
            CreditSummaryPlatformException exception,
            RetryInfo retryInfo) {
        exception.attachRetryInfo(retryInfo);
        return exception;
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

    private DataProviderContext withResponseTime(DataProviderContext base, Instant start) {
        return new DataProviderContext(
                base.protocol(),
                base.serviceId(),
                base.resolvedEndpoint(),
                base.protocolMeta(),
                elapsedMs(start),
                base.collectedAt());
    }

    private Map<String, String> buildProtocolMeta(HttpMethod httpMethod, String operation) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("httpMethod", httpMethod.name());
        meta.put("operation", operation);
        return Map.copyOf(meta);
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }

}
