package com.cfbl.platform.core.sample;

import com.cfbl.platform.core.retry.RetrySettings;
import java.util.Objects;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Holder for a preconfigured WebClient and its provider identity metadata.
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
