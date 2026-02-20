package com.kxt.credit.summary.platform.sample;

import com.kxt.credit.summary.platform.config.PlatformProperties;
import com.kxt.credit.summary.platform.exception.api.UpstreamStatusMapper;
import com.kxt.credit.summary.platform.exception.core.DataSourceContext;
import com.kxt.credit.summary.platform.exception.core.ErrorCode;
import com.kxt.credit.summary.platform.exception.core.PlatformException;
import com.kxt.credit.summary.platform.exception.core.UpstreamInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Service
public class ExampleWebClientService {

    private static final String SERVICE_ID = "sample-api";
    private static final String SAMPLE_PATH = "/sample";

    private final WebClient.Builder webClientBuilder;
    private final PlatformProperties platformProperties;
    private final Map<String, ContextTemplate> contextTemplateCache = new ConcurrentHashMap<>();

    public ExampleWebClientService(WebClient.Builder webClientBuilder, PlatformProperties platformProperties) {
        this.webClientBuilder = webClientBuilder;
        this.platformProperties = platformProperties;
    }

    public Mono<String> fetchSampleWithContext() {
        return executeRestCall(
            "GET",
            "fetchSampleWithContext",
            SAMPLE_PATH,
            client -> client.get().uri(SAMPLE_PATH),
            "Sample API GET timed out",
            "Sample API GET request failed",
            "Sample API GET failed"
        );
    }

    public Mono<String> createSampleWithContext(String customerId) {
        if (isBlank(customerId)) {
            return Mono.error(new PlatformException(ErrorCode.VALIDATION_ERROR, "customerId is required"));
        }

        return executeRestCall(
            "POST",
            "createSampleWithContext",
            SAMPLE_PATH,
            client -> client.post()
                .uri(SAMPLE_PATH)
                .bodyValue(new SampleCreateRequest(customerId)),
            "Sample API POST timed out",
            "Sample API POST request failed",
            "Sample API POST failed"
        );
    }

    private Mono<String> executeRestCall(
        String httpMethod,
        String operation,
        String path,
        Function<WebClient, WebClient.RequestHeadersSpec<?>> requestFactory,
        String timeoutMessage,
        String requestFailureMessage,
        String genericFailureMessage
    ) {
        return Mono.defer(() -> {
            PlatformProperties.ServiceDefinition svc = platformProperties.getServices().get(SERVICE_ID);
            if (svc == null || isBlank(svc.getEndpointUrl())) {
                return Mono.error(new PlatformException(
                    ErrorCode.CONFIG_ERROR,
                    "Missing endpointUrl configuration for serviceId: " + SERVICE_ID
                ));
            }

            Instant collectedAt = Instant.now();
            Instant start = collectedAt;
            String endpoint = svc.getEndpointUrl();

            ContextTemplate template = contextTemplateFor(svc, httpMethod, operation);
            DataSourceContext baseContext = new DataSourceContext(
                template.protocol(),
                template.serviceId(),
                endpoint + path,
                template.protocolMeta(),
                0L,
                collectedAt
            );

            WebClient client = webClientBuilder.baseUrl(endpoint).build();

            return requestFactory.apply(client)
                .retrieve()
                .onStatus(
                    HttpStatusCode::isError,
                    response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(rawBody -> toUpstreamException(response.statusCode(), rawBody, baseContext, start, null))
                )
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(3))
                .onErrorMap(TimeoutException.class, ex -> new PlatformException(
                    ErrorCode.UPSTREAM_TIMEOUT,
                    timeoutMessage,
                    withResponseTime(baseContext, start),
                    new UpstreamInfo(504, ex.getMessage(), elapsedMs(start)),
                    ex
                ))
                .onErrorMap(WebClientRequestException.class, ex -> new PlatformException(
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    requestFailureMessage,
                    withResponseTime(baseContext, start),
                    new UpstreamInfo(null, ex.getMessage(), elapsedMs(start)),
                    ex
                ))
                .onErrorMap(ex -> {
                    if (ex instanceof PlatformException) {
                        return ex;
                    }

                    return new PlatformException(
                        ErrorCode.UPSTREAM_BAD_GATEWAY,
                        genericFailureMessage,
                        withResponseTime(baseContext, start),
                        new UpstreamInfo(null, ex.getMessage(), elapsedMs(start)),
                        ex
                    );
                });
        });
    }

    private ContextTemplate contextTemplateFor(
        PlatformProperties.ServiceDefinition svc,
        String httpMethod,
        String operation
    ) {
        String key = templateKey(SERVICE_ID, httpMethod, operation, svc.getOpenapiVersion());

        return contextTemplateCache.computeIfAbsent(
            key,
            ignored -> new ContextTemplate(
                DataSourceContext.Protocol.REST,
                SERVICE_ID,
                buildProtocolMeta(svc, httpMethod, operation)
            )
        );
    }

    private String templateKey(String serviceId, String httpMethod, String operation, String openapiVersion) {
        return String.join("|", serviceId, httpMethod, operation, String.valueOf(openapiVersion));
    }

    private PlatformException toUpstreamException(
        HttpStatusCode statusCode,
        String rawBody,
        DataSourceContext baseContext,
        Instant start,
        Throwable cause
    ) {
        int rawStatus = statusCode.value();
        long responseTimeMs = elapsedMs(start);
        ErrorCode code = UpstreamStatusMapper.mapHttpStatus(rawStatus);

        String message = "Sample API returned HTTP " + rawStatus;
        String rawMessage = isBlank(rawBody) ? statusCode.toString() : rawBody;

        return new PlatformException(
            code,
            message,
            withResponseTime(baseContext, start),
            new UpstreamInfo(rawStatus, rawMessage, responseTimeMs),
            cause
        );
    }

    private DataSourceContext withResponseTime(DataSourceContext base, Instant start) {
        return new DataSourceContext(
            base.protocol(),
            base.serviceId(),
            base.resolvedEndpoint(),
            base.protocolMeta(),
            elapsedMs(start),
            base.collectedAt()
        );
    }

    private Map<String, String> buildProtocolMeta(
        PlatformProperties.ServiceDefinition svc,
        String httpMethod,
        String operation
    ) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("httpMethod", httpMethod);
        meta.put("operation", operation);

        if (!isBlank(svc.getOpenapiVersion())) {
            meta.put("openapiVersion", svc.getOpenapiVersion());
        }

        return Map.copyOf(meta);
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ContextTemplate(
        DataSourceContext.Protocol protocol,
        String serviceId,
        Map<String, String> protocolMeta
    ) {
    }

    private record SampleCreateRequest(String customerId) {
    }
}
