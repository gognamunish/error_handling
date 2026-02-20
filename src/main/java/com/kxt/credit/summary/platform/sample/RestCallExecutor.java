package com.kxt.credit.summary.platform.sample;

import com.cfbl.platform.core.exception.api.ApiResponse;
import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.exception.core.UpstreamInfo;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Executes outbound REST calls and maps results into {@link ApiResponse}.
 */
public class RestCallExecutor {

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

            return requestFactory.apply(holder.webClient())
                    .exchangeToMono(response -> mapResponse(
                            response.statusCode(),
                            response.bodyToMono(bodyType),
                            baseContext,
                            start))
                    .timeout(Duration.ofSeconds(3))
                    .onErrorMap(ex -> toPlatformException(ex, failureMessage, baseContext, start));
        });
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
