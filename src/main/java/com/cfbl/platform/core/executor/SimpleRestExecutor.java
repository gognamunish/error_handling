package com.cfbl.platform.core.executor;

import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.exception.core.UpstreamInfo;
import com.cfbl.platform.core.integration.model.ProviderResult;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Lightweight REST executor for simple WebClient calls without retry orchestration.
 */
@Component
public class SimpleRestExecutor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
    private static final String DEFAULT_OPERATION = "simpleRestCall";
    private static final String DEFAULT_FAILURE_MESSAGE = "REST call failed";

    /**
     * Executes simple REST call and returns integration-layer result (no API envelope coupling).
     */
    public <T> Mono<ProviderResult<T>> executeProvider(
            WebClient webClient,
            String serviceId,
            String uri,
            String operation,
            Function<WebClient, WebClient.RequestHeadersSpec<?>> requestFactory,
            Type responseType,
            String failureMessage) {
        Objects.requireNonNull(webClient, "webClient");
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(requestFactory, "requestFactory");
        Objects.requireNonNull(responseType, "responseType");
        Objects.requireNonNull(failureMessage, "failureMessage");

        return Mono.defer(() -> {
            @SuppressWarnings("unchecked")
            ParameterizedTypeReference<T> bodyType =
                    (ParameterizedTypeReference<T>) ParameterizedTypeReference.forType(responseType);

            Instant start = Instant.now();
            DataProviderContext baseContext = new DataProviderContext(
                    DataProviderContext.Protocol.REST,
                    serviceId,
                    uri,
                    Map.of("operation", operation),
                    0L,
                    start);

            return requestFactory.apply(webClient)
                    .exchangeToMono(response ->
                            mapResponse(response.statusCode(), response.bodyToMono(bodyType), baseContext, start))
                    .timeout(DEFAULT_TIMEOUT)
                    .onErrorMap(ex -> toPlatformException(ex, failureMessage, baseContext, start));
        });
    }

    /**
     * Executes simple REST call with default operation/failure labels.
     */
    public <T> Mono<ProviderResult<T>> executeProvider(
            WebClient webClient,
            String serviceId,
            String uri,
            Function<WebClient, WebClient.RequestHeadersSpec<?>> requestFactory,
            Type responseType) {
        return executeProvider(
                webClient,
                serviceId,
                uri,
                DEFAULT_OPERATION,
                requestFactory,
                responseType,
                DEFAULT_FAILURE_MESSAGE);
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

    private DataProviderContext withResponseTime(DataProviderContext baseContext, Instant start) {
        return new DataProviderContext(
                baseContext.protocol(),
                baseContext.serviceId(),
                baseContext.endpoint(),
                baseContext.protocolAttributes(),
                elapsedMs(start),
                baseContext.collectedAt());
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }
}
