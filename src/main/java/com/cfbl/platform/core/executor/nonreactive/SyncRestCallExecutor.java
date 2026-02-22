package com.cfbl.platform.core.executor.nonreactive;

import com.cfbl.platform.core.exception.core.CreditSummaryDataCollectionException;
import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.exception.core.UpstreamInfo;
import com.cfbl.platform.core.integration.model.ProviderResult;
import com.cfbl.platform.core.retry.RetrySettings;
import com.cfbl.platform.core.retry.SyncRetryPolicyExecutor;
import java.io.IOException;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * Executes outbound synchronous REST calls and maps results into
 * {@link ProviderResult}.
 * <p>
 * This executor prevents context loss by executing Resilience4j retry blocks
 * strictly
 * on the calling thread, maintaining automatic access to ThreadLocal
 * SecurityContext variables.
 */
@Component
public class SyncRestCallExecutor extends SyncExecutorBase {

    public SyncRestCallExecutor(SyncRetryPolicyExecutor retryExecutor) {
        super(retryExecutor);
    }

    public ProviderResult<String> executeProvider(
            RestTemplateHolder holder,
            HttpMethod httpMethod,
            String operation,
            String path,
            Supplier<ResponseEntity<String>> requestFactory,
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

    public ProviderResult<String> executeProvider(
            RestTemplateHolder holder,
            HttpMethod httpMethod,
            String operation,
            String path,
            Supplier<ResponseEntity<String>> requestFactory,
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
            Supplier<ResponseEntity<String>> requestFactory,
            DataProviderContext baseContext,
            Instant start) {
        try {
            ResponseEntity<String> response = requestFactory.get();
            return mapResponse(response.getStatusCode(), response.getBody(), baseContext, start);
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
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
        } catch (ResourceAccessException ex) {
            // ResourceAccessException wraps SocketTimeoutException/ConnectException in
            // RestTemplate
            throw new CreditSummaryDataCollectionException(
                    ErrorCode.LAYER_DATA_COLLECTION_FAILURE,
                    "Provider transport or timeout error",
                    withResponseTime(baseContext, start),
                    new UpstreamInfo(null, ex.getMessage(), elapsedMs(start)),
                    ex);
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

        // Technically dead code if RestTemplate uses default
        // DefaultResponseErrorHandler
        // However some consumers disable the error handler to consume 4xx/5xx responses
        // normally.
        throw new CreditSummaryDataCollectionException(
                ErrorCode.LAYER_DATA_COLLECTION_FAILURE,
                "Upstream returned HTTP " + statusCode.value() + " Response: " + (body != null ? body : ""),
                withResponseTime(baseContext, start),
                new UpstreamInfo(statusCode.value(), statusCode.toString(), elapsedMs(start)),
                null);
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof ResourceAccessException || throwable instanceof TimeoutException) {
            return true;
        }

        if (throwable instanceof CreditSummaryDataCollectionException ex) {
            UpstreamInfo upstream = ex.getUpstream();
            if (upstream == null || upstream.httpStatus() == null) {
                // Determine if it was caused by inner timeout
                if (ex.getCause() instanceof ResourceAccessException) {
                    return true;
                }
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
