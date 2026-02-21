package com.cfbl.platform.core.executor;

import com.cfbl.platform.core.retry.RetrySettings;
import java.util.Objects;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Immutable holder for a preconfigured REST client and provider metadata.
 *
 * <p>Used by {@link RestCallExecutor} so execution logic stays independent from how the client
 * was created.
 */
public record WebClientHolder(
    String serviceId,
    String endpointUrl,
    WebClient webClient,
    RetrySettings retrySettings
) {

    public WebClientHolder(String serviceId, String endpointUrl, WebClient webClient) {
        this(serviceId, endpointUrl, webClient, RetrySettings.defaults());
    }

    public WebClientHolder {
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(endpointUrl, "endpointUrl");
        Objects.requireNonNull(webClient, "webClient");
        retrySettings = retrySettings == null ? RetrySettings.defaults() : retrySettings;
    }
}
